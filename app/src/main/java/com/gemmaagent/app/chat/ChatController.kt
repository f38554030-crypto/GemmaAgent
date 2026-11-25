package com.gemmaagent.app.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.gemmaagent.app.data.ModelRepository
import com.gemmaagent.app.data.SettingsRepository
import com.gemmaagent.app.data.db.AppDatabase
import com.gemmaagent.app.data.db.Kinds
import com.gemmaagent.app.data.db.MemoryEntity
import com.gemmaagent.app.data.db.MessageEntity
import com.gemmaagent.app.data.db.NoteEntity
import com.gemmaagent.app.data.db.Roles
import com.gemmaagent.app.data.db.SessionEntity
import com.gemmaagent.app.docs.DocumentReader
import com.gemmaagent.app.docs.LoadedDocument
import com.gemmaagent.app.llm.ChatTurn
import com.gemmaagent.app.llm.EngineParams
import com.gemmaagent.app.llm.GenRequest
import com.gemmaagent.app.llm.GenResult
import com.gemmaagent.app.llm.LlmEngine
import com.gemmaagent.app.llm.PromptBuilder
import com.gemmaagent.app.tools.Calculator
import com.gemmaagent.app.web.WebResult
import com.gemmaagent.app.web.WebSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Status jawaban yang sedang dialirkan (streaming). */
data class StreamState(
    val messageId: Long,
    val text: String = "",
    val thought: String = "",
    val phase: String = "",
    /** Untuk Mode Banding: teks jawaban cepat yang sudah selesai. */
    val compareFast: String? = null,
)

sealed interface DefineState {
    data class Loading(val word: String) : DefineState
    data class Result(val word: String, val text: String) : DefineState
}

data class UsageStats(
    val sessions: Int,
    val userMessages: Int,
    val aiMessages: Int,
    val onlineAnswers: Int,
    val avgTokensPerSec: Double,
    val totalTokens: Long,
    val notes: Int,
    val memories: Int,
)

class ChatController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val db: AppDatabase,
    private val llm: LlmEngine,
    private val settings: SettingsRepository,
    private val models: ModelRepository,
) {
    private val _sessionId = MutableStateFlow(settings.lastSessionId())
    val sessionId: StateFlow<Long?> = _sessionId.asStateFlow()

    val sessions: Flow<List<SessionEntity>> = db.sessions().observeAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: Flow<List<MessageEntity>> = _sessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else db.messages().observe(id)
    }

    val starred: Flow<List<MessageEntity>> = db.messages().observeStarred()
    val notes = db.notes().observeAll()
    val memories = db.memories().observeAll()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _stream = MutableStateFlow<StreamState?>(null)
    val stream: StateFlow<StreamState?> = _stream.asStateFlow()

    private val _define = MutableStateFlow<DefineState?>(null)
    val define: StateFlow<DefineState?> = _define.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var job: Job? = null
    private var followJob: Job? = null

    /** Hentikan pembuatan saran lanjutan (termasuk di mesin native) agar perintah baru tidak menunggu. */
    private fun cancelFollowUps() {
        if (followJob?.isActive == true) {
            llm.stop()
            followJob?.cancel()
        }
    }

    /** Setelah gambar pernah dikirim, mesin tetap dalam mode vision agar tidak bolak-balik memuat ulang. */
    @Volatile
    private var visionSticky = false

    init {
        // Pastikan sesi terakhir masih ada.
        scope.launch {
            val id = _sessionId.value
            if (id != null && db.sessions().get(id) == null) setSession(null)
        }
    }

    private fun emit(msg: String) {
        _events.tryEmit(msg)
    }

    fun engineParams(vision: Boolean = visionSticky): EngineParams? {
        val path = models.currentModelPath() ?: return null
        val s = settings.current
        return EngineParams(path, s.cpuThreads, s.contextTokens, vision)
    }

    /** Memuat model di latar agar jawaban pertama lebih cepat. */
    fun preload() {
        val p = engineParams()
        if (p == null) llm.markNoModel() else llm.loadAsync(p)
    }

    // ============================== SESI ==============================

    fun setSession(id: Long?) {
        _sessionId.value = id
        settings.setLastSessionId(id)
    }

    fun newSession() {
        if (_generating.value) stop()
        setSession(null)
    }

    fun renameSession(id: Long, title: String) = scope.launch { db.sessions().rename(id, title.trim().take(80).ifBlank { "Percakapan" }) }
    fun togglePin(s: SessionEntity) = scope.launch { db.sessions().setPinned(s.id, !s.pinned) }

    fun deleteSession(id: Long) = scope.launch {
        db.messages().deleteSession(id)
        db.sessions().delete(id)
        if (_sessionId.value == id) setSession(null)
    }

    suspend fun searchSessions(query: String, all: List<SessionEntity>): List<SessionEntity> {
        if (query.isBlank()) return all
        val byText = db.messages().sessionsMatching(query.trim()).toSet()
        return all.filter { it.title.contains(query, true) || it.id in byText }
    }

    private suspend fun ensureSession(firstText: String): Long {
        _sessionId.value?.let { return it }
        val title = firstText.lineSequence().firstOrNull().orEmpty().trim().take(42).ifBlank { "Percakapan baru" }
        val id = db.sessions().insert(SessionEntity(title = title))
        setSession(id)
        return id
    }

    // ============================== KIRIM ==============================

    fun send(text: String, imageUri: Uri? = null, document: LoadedDocument? = null, compare: Boolean = false) {
        val trimmed = text.trim()
        if (_generating.value) {
            emit("Tunggu jawaban selesai atau tekan Stop dulu.")
            return
        }
        if (trimmed.isEmpty() && imageUri == null && document == null) return
        cancelFollowUps()

        scope.launch {
            try {
                val sid = ensureSession(trimmed.ifEmpty { document?.name ?: "Gambar" })

                // Kalkulator & konverter lokal: tanpa LLM, presisi.
                if (imageUri == null && document == null) {
                    Calculator.tryHandle(trimmed)?.let { r ->
                        db.messages().insert(MessageEntity(sessionId = sid, role = Roles.USER, text = trimmed))
                        db.messages().insert(
                            MessageEntity(
                                sessionId = sid, role = Roles.ASSISTANT, kind = Kinds.CALC,
                                text = "🧮 **${r.value}**\n\n`${r.expression}` — dihitung oleh kalkulator lokal (bukan AI).",
                            )
                        )
                        db.sessions().touch(sid)
                        return@launch
                    }
                }

                // Memori jangka panjang otomatis dari kalimat seperti "nama saya …", "ingat bahwa …".
                if (settings.current.useMemory) {
                    MemoryExtractor.extract(trimmed).forEach { fact ->
                        if (db.memories().countSame(fact) == 0) {
                            db.memories().insert(MemoryEntity(fact = fact))
                            emit("🧠 Diingat: $fact")
                        }
                    }
                }

                val imagePath = imageUri?.let { withContext(Dispatchers.IO) { saveImage(it) } }
                val userMsg = MessageEntity(
                    sessionId = sid,
                    role = Roles.USER,
                    text = trimmed.ifEmpty { if (imagePath != null) "Jelaskan gambar ini." else "Rangkum dokumen ini." },
                    imagePath = imagePath,
                    attachmentName = document?.name ?: imagePath?.let { "Gambar" },
                    attachmentText = document?.text,
                )
                val id = db.messages().insert(userMsg)
                db.sessions().touch(sid)
                respond(userMsg.copy(id = id), compare)
            } catch (t: Throwable) {
                emit("Gagal mengirim: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    /** Regenerasi jawaban (fitur 39). */
    fun regenerate(assistant: MessageEntity) {
        if (_generating.value) return
        cancelFollowUps()
        scope.launch {
            val list = db.messages().list(assistant.sessionId)
            val user = list.lastOrNull { it.id < assistant.id && it.role == Roles.USER } ?: return@launch
            db.messages().deleteFrom(assistant.sessionId, assistant.id)
            respond(user, compare = assistant.kind == Kinds.COMPARE)
        }
    }

    /** Edit pesan & kirim ulang (fitur 38). */
    fun editAndResend(user: MessageEntity, newText: String) {
        if (_generating.value || newText.isBlank()) return
        cancelFollowUps()
        scope.launch {
            db.messages().deleteFrom(user.sessionId, user.id)
            val edited = user.copy(id = 0, text = newText.trim(), createdAt = System.currentTimeMillis())
            val id = db.messages().insert(edited)
            respond(edited.copy(id = id), compare = false)
        }
    }

    /** Tombol Stop Generasi Instan (fitur 37). */
    fun stop() {
        llm.stop()
        job?.cancel()
        followJob?.cancel()
    }

    private fun respond(user: MessageEntity, compare: Boolean) {
        job = scope.launch {
            _generating.value = true
            val placeholder = MessageEntity(
                sessionId = user.sessionId, role = Roles.ASSISTANT, text = "",
                kind = if (compare) Kinds.COMPARE else Kinds.TEXT,
            )
            val aid = db.messages().insert(placeholder)
            _stream.value = StreamState(aid, phase = "Menyiapkan…")
            var citations: List<WebResult> = emptyList()
            var usedOnline = false
            try {
                val s = settings.current
                if (user.imagePath != null) visionSticky = true
                val params = engineParams(visionSticky) ?: throw IllegalStateException(
                    "Belum ada model. Buka Pengaturan → Model untuk memilih file .litertlm."
                )
                val outputReserve = minOf(s.maxOutputTokens, s.contextTokens / 2)
                val budgetChars = ((s.contextTokens - outputReserve - 150) * 2.8).toInt().coerceAtLeast(600)

                // ---------- Konteks tambahan: dokumen / URL / pencarian web ----------
                var prompt = user.text
                val doc = user.attachmentText
                if (doc != null) {
                    prompt = buildDocumentPrompt(params, user, doc, budgetChars, s.temperature, aid)
                } else if (s.online) {
                    if (!WebSearch.isOnline(context)) {
                        emit("📴 Tidak ada internet — menjawab secara offline.")
                    } else {
                        val url = WebSearch.findUrl(user.text)
                        if (url != null) {
                            _stream.value = StreamState(aid, phase = "🌐 Membaca tautan…")
                            val art = runCatching { WebSearch.readArticle(url, (budgetChars * 0.8).toInt()) }.getOrNull()
                            if (art != null && art.snippet.length > 80) {
                                citations = listOf(WebResult(art.title, url, ""))
                                prompt = PromptBuilder.urlSummary(user.text.replace(url, "").trim(), art.title, art.snippet)
                                usedOnline = true
                            } else emit("Tautan tidak bisa dibaca — menjawab tanpa isi tautan.")
                        } else {
                            _stream.value = StreamState(aid, phase = "🔎 Mencari: ${s.searchSource.label}…")
                            val results = runCatching { WebSearch.search(user.text, s.searchSource) }.getOrDefault(emptyList())
                            if (results.isNotEmpty()) {
                                citations = results
                                prompt = PromptBuilder.withWebContext(user.text, results, (budgetChars * 0.6).toInt())
                                usedOnline = true
                            } else emit("Pencarian web tidak memberi hasil — menjawab offline.")
                        }
                    }
                }

                // ---------- Riwayat (sliding window) ----------
                val history = buildHistory(user, s.historyTurns, budgetChars - prompt.length)
                val memories = if (s.useMemory) db.memories().all().map { it.fact } else emptyList()
                val system = PromptBuilder.systemPrompt(s.personaId, s.customSystemPrompt, s.thinking, memories, usedOnline)
                val image = user.imagePath?.let { p -> withContext(Dispatchers.IO) { runCatching { File(p).readBytes() }.getOrNull() } }

                val citeJson = JSONArray().apply {
                    citations.forEach { put(JSONObject().put("title", it.title).put("url", it.url)) }
                }

                if (compare) {
                    _stream.value = StreamState(aid, phase = "⚡ Mode Cepat…")
                    val fast = llm.generate(params, GenRequest(
                        PromptBuilder.systemPrompt(s.personaId, s.customSystemPrompt, false, memories, usedOnline),
                        history, prompt, image, s.temperature, s.maxOutputTokens, thinking = false,
                    )) { t, th -> _stream.value = StreamState(aid, t, th, "⚡ Mode Cepat") }
                    if (fast.stopped) throw CancellationException("stop")
                    _stream.value = StreamState(aid, phase = "🧠 Mode Mendalam…", compareFast = fast.text)
                    val deep = llm.generate(params, GenRequest(
                        PromptBuilder.systemPrompt(s.personaId, s.customSystemPrompt, true, memories, usedOnline),
                        history, prompt, image, s.temperature, s.maxOutputTokens, thinking = true,
                    )) { t, th -> _stream.value = StreamState(aid, t, th, "🧠 Mode Mendalam", compareFast = fast.text) }
                    val payload = JSONObject()
                        .put("fast", resultJson(fast))
                        .put("deep", resultJson(deep))
                        .put("citations", citeJson)
                    db.messages().update(
                        placeholder.copy(
                            id = aid, text = fast.text.ifBlank { deep.text }, thought = deep.thought, payload = payload.toString(),
                            online = usedOnline, tokensPerSec = deep.tokensPerSec, tokenCount = fast.tokens + deep.tokens,
                            durationMs = fast.durationMs + deep.durationMs,
                        )
                    )
                } else {
                    _stream.value = StreamState(aid, phase = if (s.thinking) "🧠 Berpikir…" else "✍️ Menulis…")
                    val r = llm.generate(params, GenRequest(system, history, prompt, image, s.temperature, s.maxOutputTokens, s.thinking)) { t, th ->
                        _stream.value = StreamState(aid, t, th, if (s.thinking && t.isBlank()) "🧠 Berpikir…" else "✍️ Menulis…")
                    }
                    val text = when {
                        r.stopped -> r.text + "\n\n_(dihentikan)_"
                        r.text.isBlank() -> "_(Model tidak menghasilkan jawaban. Coba Regenerasi atau mulai Sesi Baru.)_"
                        else -> r.text
                    }
                    val payload = JSONObject().put("citations", citeJson)
                    db.messages().update(
                        placeholder.copy(
                            id = aid, text = text, thought = r.thought, payload = payload.toString(), online = usedOnline,
                            tokensPerSec = r.tokensPerSec, tokenCount = r.tokens, durationMs = r.durationMs,
                        )
                    )
                    if (s.followUps && !r.stopped && r.text.isNotBlank()) launchFollowUps(params, aid, user.text, r.text)
                }
                db.sessions().touch(user.sessionId)
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    val partial = _stream.value?.text.orEmpty()
                    db.messages().update(
                        placeholder.copy(id = aid, text = partial + "\n\n_(dihentikan)_", payload = "")
                    )
                }
            } catch (t: Throwable) {
                db.messages().update(
                    placeholder.copy(id = aid, kind = Kinds.ERROR, text = "⚠️ ${t.message ?: t.javaClass.simpleName}")
                )
            } finally {
                _stream.value = null
                _generating.value = false
            }
        }
    }

    private fun resultJson(r: GenResult) = JSONObject()
        .put("text", r.text).put("thought", r.thought).put("tps", r.tokensPerSec).put("tokens", r.tokens)

    /** Pemecahan dokumen hemat RAM (fitur 9): rangkum per bagian, lalu jawab dari gabungan ringkasan. */
    private suspend fun buildDocumentPrompt(
        params: EngineParams,
        user: MessageEntity,
        doc: String,
        budgetChars: Int,
        temperature: Float,
        aid: Long,
    ): String {
        val name = user.attachmentName ?: "dokumen"
        val question = user.text
        val room = (budgetChars - question.length - 200).coerceAtLeast(500)
        if (doc.length <= room) return PromptBuilder.withDocument(question, name, doc)

        val chunks = DocumentReader.chunk(doc, room).take(MAX_CHUNKS)
        val summaries = StringBuilder()
        chunks.forEachIndexed { i, c ->
            _stream.value = StreamState(aid, phase = "📄 Merangkum bagian ${i + 1}/${chunks.size}…")
            val r = llm.generate(
                params,
                GenRequest(
                    "Kamu perangkum dokumen yang teliti. Jawab dalam Bahasa Indonesia.",
                    emptyList(), PromptBuilder.chunkSummary(name, i + 1, chunks.size, c), null,
                    temperature = 0.2f, maxOutputTokens = 220, thinking = false,
                ),
            ) { t, _ -> _stream.value = StreamState(aid, t, "", "📄 Merangkum bagian ${i + 1}/${chunks.size}…") }
            if (r.stopped) throw CancellationException("stop")
            summaries.append("Bagian ${i + 1}:\n").append(r.text.take(room / chunks.size + 200)).append("\n")
        }
        if (doc.length > chunks.sumOf { it.length }) emit("Dokumen sangat panjang: hanya ${chunks.size} bagian pertama yang dirangkum.")
        return PromptBuilder.withDocument(question, "$name (ringkasan per bagian)", summaries.toString().take(room))
    }

    private suspend fun buildHistory(user: MessageEntity, turns: Int, budgetChars: Int): List<ChatTurn> {
        val all = db.messages().list(user.sessionId).filter { it.id < user.id && it.kind != Kinds.ERROR && it.text.isNotBlank() }
        val window = all.takeLast(turns * 2).map {
            ChatTurn(it.role == Roles.USER, it.text.replace("_(dihentikan)_", "").trim().take(700))
        }.toMutableList()
        // Buang giliran tertua sampai muat di anggaran token.
        while (window.isNotEmpty() && window.sumOf { it.text.length } > budgetChars.coerceAtLeast(0)) window.removeAt(0)
        while (window.isNotEmpty() && !window.first().user) window.removeAt(0)
        return window
    }

    // ============================== SARAN LANJUTAN ==============================

    private fun launchFollowUps(params: EngineParams, aid: Long, question: String, answer: String) {
        followJob = scope.launch {
            try {
                val r = llm.generate(
                    params,
                    GenRequest(
                        "Kamu membantu menyarankan pertanyaan lanjutan. Bahasa Indonesia.",
                        emptyList(), PromptBuilder.followUps(question, answer), null,
                        temperature = 0.6f, maxOutputTokens = 80, thinking = false,
                    ),
                ) { _, _ -> }
                if (r.stopped) return@launch
                val list = r.text.lines()
                    .map { it.trim().trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim() }
                    .filter { it.length in 4..90 }
                    .distinct().take(3)
                if (list.isEmpty()) return@launch
                val msg = db.messages().get(aid) ?: return@launch
                val payload = runCatching { JSONObject(msg.payload) }.getOrDefault(JSONObject())
                payload.put("followups", JSONArray(list))
                db.messages().setPayload(aid, payload.toString())
            } catch (_: Throwable) {
            }
        }
    }

    // ============================== ALAT BELAJAR ==============================

    /** Generator Kuis / Flashcard (fitur 18). */
    fun makeStudy(source: MessageEntity, quiz: Boolean) {
        if (_generating.value) return
        cancelFollowUps()
        job = scope.launch {
            _generating.value = true
            val aid = db.messages().insert(
                MessageEntity(sessionId = source.sessionId, role = Roles.ASSISTANT, text = "", kind = if (quiz) Kinds.QUIZ else Kinds.FLASHCARDS)
            )
            _stream.value = StreamState(aid, phase = if (quiz) "📝 Membuat kuis…" else "🃏 Membuat flashcard…")
            try {
                val params = engineParams() ?: throw IllegalStateException("Belum ada model.")
                val r = llm.generate(
                    params,
                    GenRequest(
                        "Kamu pembuat materi belajar. Balas hanya JSON valid.", emptyList(),
                        if (quiz) PromptBuilder.quiz(source.text) else PromptBuilder.flashcards(source.text), null,
                        temperature = 0.3f, maxOutputTokens = 700, thinking = false,
                    ),
                ) { t, _ -> _stream.value = StreamState(aid, t, "", if (quiz) "📝 Membuat kuis…" else "🃏 Membuat flashcard…") }
                val json = extractJson(r.text)
                val ok = json != null && (if (quiz) json.optJSONArray("questions") else json.optJSONArray("cards"))?.length()?.let { it > 0 } == true
                val msg = db.messages().get(aid) ?: return@launch
                if (ok) {
                    db.messages().update(msg.copy(text = if (quiz) "📝 Kuis siap — pilih jawabanmu." else "🃏 Flashcard siap — ketuk kartu untuk membalik.", payload = json.toString(), tokensPerSec = r.tokensPerSec, tokenCount = r.tokens))
                } else {
                    db.messages().update(msg.copy(kind = Kinds.TEXT, text = r.text.ifBlank { "Gagal membuat materi." } + "\n\n_(Format JSON tidak valid, ditampilkan sebagai teks.)_"))
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { db.messages().delete(aid) }
            } catch (t: Throwable) {
                db.messages().get(aid)?.let { db.messages().update(it.copy(kind = Kinds.ERROR, text = "⚠️ ${t.message}")) }
            } finally {
                _stream.value = null
                _generating.value = false
            }
        }
    }

    /** Ekstraktor daftar tugas interaktif (fitur 19) — lokal, tanpa LLM. */
    fun makeChecklist(source: MessageEntity) = scope.launch {
        val items = source.text.lines().map { it.trim() }
            .filter { Regex("^([-*•]|\\d+[.)]|- \\[[ xX]])\\s+").containsMatchIn(it) }
            .map { it.replace(Regex("^([-*•]|\\d+[.)])\\s+"), "").replace(Regex("^\\[[ xX]]\\s*"), "").replace("**", "").trim() }
            .filter { it.isNotBlank() }
        val finalItems = items.ifEmpty {
            source.text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.length > 8 }.take(10)
        }
        if (finalItems.isEmpty()) {
            emit("Tidak ada daftar yang bisa dijadikan checklist.")
            return@launch
        }
        val arr = JSONArray()
        finalItems.take(25).forEach { arr.put(JSONObject().put("t", it).put("d", false)) }
        db.messages().insert(
            MessageEntity(
                sessionId = source.sessionId, role = Roles.ASSISTANT, kind = Kinds.CHECKLIST,
                text = "✅ Daftar tugas (${finalItems.size.coerceAtMost(25)} item)", payload = JSONObject().put("items", arr).toString(),
            )
        )
    }

    fun toggleChecklist(msg: MessageEntity, index: Int) = scope.launch {
        val json = runCatching { JSONObject(msg.payload) }.getOrNull() ?: return@launch
        val items = json.optJSONArray("items") ?: return@launch
        val item = items.optJSONObject(index) ?: return@launch
        item.put("d", !item.optBoolean("d"))
        db.messages().setPayload(msg.id, json.toString())
    }

    /** Kamus & penjelas kata kontekstual (fitur 11). */
    fun defineWord(word: String, sentence: String) {
        if (_generating.value) {
            emit("Tunggu jawaban selesai dulu untuk membuka kamus.")
            return
        }
        val params = engineParams() ?: run { emit("Belum ada model."); return }
        cancelFollowUps()
        _define.value = DefineState.Loading(word)
        scope.launch {
            _generating.value = true
            try {
                val r = llm.generate(
                    params,
                    GenRequest("Kamu kamus bahasa yang ringkas.", emptyList(), PromptBuilder.define(word, sentence), null, 0.2f, 120, false),
                ) { t, _ -> if (_define.value != null) _define.value = DefineState.Result(word, t) }
                if (_define.value != null) _define.value = DefineState.Result(word, r.text.ifBlank { "Tidak ada definisi." })
            } catch (t: Throwable) {
                if (_define.value != null) _define.value = DefineState.Result(word, "Gagal: ${t.message}")
            } finally {
                _generating.value = false
            }
        }
    }

    fun closeDefine() {
        if (_define.value is DefineState.Loading) llm.stop()
        _define.value = null
    }

    // ============================== FAVORIT, CATATAN, MEMORI ==============================

    fun toggleStar(m: MessageEntity) = scope.launch { db.messages().setStarred(m.id, !m.starred) }

    fun saveNote(m: MessageEntity) = scope.launch {
        val title = m.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().replace(Regex("[#*`>]"), "").trim().take(60)
        db.notes().insert(NoteEntity(title = title.ifBlank { "Catatan" }, text = m.text, sourceMessageId = m.id))
        emit("📒 Disimpan ke Catatan")
    }

    fun updateNote(n: NoteEntity) = scope.launch { db.notes().update(n) }
    fun deleteNote(id: Long) = scope.launch { db.notes().delete(id) }

    fun addMemory(fact: String) = scope.launch {
        val f = fact.trim()
        if (f.isNotEmpty() && db.memories().countSame(f) == 0) db.memories().insert(MemoryEntity(fact = f.take(300)))
    }

    fun updateMemory(m: MemoryEntity) = scope.launch { db.memories().update(m) }
    fun deleteMemory(id: Long) = scope.launch { db.memories().delete(id) }
    fun clearMemories() = scope.launch { db.memories().deleteAll() }

    suspend fun stats(): UsageStats = withContext(Dispatchers.IO) {
        UsageStats(
            sessions = db.sessions().count(),
            userMessages = db.messages().countRole(Roles.USER),
            aiMessages = db.messages().countRole(Roles.ASSISTANT),
            onlineAnswers = db.messages().countOnline(),
            avgTokensPerSec = db.messages().avgTokensPerSec() ?: 0.0,
            totalTokens = db.messages().totalTokens() ?: 0L,
            notes = db.notes().count(),
            memories = db.memories().all().size,
        )
    }

    // ============================== UTIL ==============================

    /** Menyimpan gambar terkecil yang masih jelas (maks 768 px) agar hemat RAM. */
    private fun saveImage(uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 768) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("Gambar tidak bisa dibuka")
        val scale = 768f / maxOf(bmp.width, bmp.height)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (scaled !== bmp) bmp.recycle()
        scaled.recycle()
        return file.absolutePath
    }

    private fun extractJson(raw: String): JSONObject? {
        val s = raw.replace("```json", "").replace("```", "")
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(s.substring(start, end + 1)) }.getOrNull()
    }

    companion object {
        private const val MAX_CHUNKS = 8
    }
}

/** Ekstraksi fakta pengguna berbasis aturan (tanpa beban LLM tambahan). */
object MemoryExtractor {
    private val patterns = listOf(
        Regex("^(?:tolong\\s+)?ingat(?:lah)?(?:\\s+bahwa)?[:,]?\\s+(.{4,200})$", RegexOption.IGNORE_CASE) to { m: MatchResult -> m.groupValues[1] },
        Regex("\\bnama (?:saya|aku|gua|gue)(?: adalah)?\\s+([A-Za-zÀ-ÿ' ]{2,40})", RegexOption.IGNORE_CASE) to { m: MatchResult -> "Nama pengguna: ${m.groupValues[1].trim().split(' ').take(3).joinToString(" ")}" },
        Regex("\\bpanggil (?:saya|aku|gua|gue)\\s+([A-Za-zÀ-ÿ' ]{2,30})", RegexOption.IGNORE_CASE) to { m: MatchResult -> "Pengguna ingin dipanggil: ${m.groupValues[1].trim().split(' ').take(2).joinToString(" ")}" },
        Regex("\\b(?:saya|aku|gua|gue) (?:bekerja sebagai|kerja sebagai|seorang|adalah seorang)\\s+([^.,!?]{3,60})", RegexOption.IGNORE_CASE) to { m: MatchResult -> "Profesi/peran pengguna: ${m.groupValues[1].trim()}" },
        Regex("\\b(?:saya|aku|gua|gue) (?:tinggal|berdomisili) di\\s+([^.,!?]{3,50})", RegexOption.IGNORE_CASE) to { m: MatchResult -> "Domisili pengguna: ${m.groupValues[1].trim()}" },
        Regex("\\b(?:saya|aku|gua|gue) (?:suka|senang|hobi)\\s+([^.,!?]{3,60})", RegexOption.IGNORE_CASE) to { m: MatchResult -> "Pengguna suka ${m.groupValues[1].trim()}" },
        Regex("\\b(?:saya|aku|gua|gue) (?:tidak suka|nggak suka|gak suka|benci)\\s+([^.,!?]{3,60})", RegexOption.IGNORE_CASE) to { m: MatchResult -> "Pengguna tidak suka ${m.groupValues[1].trim()}" },
    )

    fun extract(text: String): List<String> {
        if (text.length > 400) return emptyList()
        return patterns.mapNotNull { (re, f) -> re.find(text.trim())?.let(f)?.trim()?.trimEnd('.') }
            .filter { it.length in 4..220 }
            .distinct()
    }
}
