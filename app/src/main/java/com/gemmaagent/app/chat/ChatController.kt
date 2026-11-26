package com.gemmaagent.app.chat

import android.content.Context
import android.net.Uri
import com.gemmaagent.app.data.ModelFile
import com.gemmaagent.app.data.ModelRepository
import com.gemmaagent.app.data.SettingsRepository
import com.gemmaagent.app.data.db.AppDatabase
import com.gemmaagent.app.data.db.Kinds
import com.gemmaagent.app.data.db.MemoryEntity
import com.gemmaagent.app.data.db.MessageEntity
import com.gemmaagent.app.data.db.NoteEntity
import com.gemmaagent.app.data.db.Roles
import com.gemmaagent.app.data.db.SessionEntity
import com.gemmaagent.app.data.kb.KbChunkFts
import com.gemmaagent.app.data.kb.KbDocEntity
import com.gemmaagent.app.data.kb.KnowledgeDb
import com.gemmaagent.app.docs.DocumentReader
import com.gemmaagent.app.docs.LoadedDocument
import com.gemmaagent.app.llm.ChatTurn
import com.gemmaagent.app.llm.GenRequest
import com.gemmaagent.app.llm.GenResult
import com.gemmaagent.app.llm.ModelSettings
import com.gemmaagent.app.llm.ModelSettingsStore
import com.gemmaagent.app.llm.PromptBuilder
import com.gemmaagent.app.llm.PromptChain
import com.gemmaagent.app.llm.UnifiedLlmEngine
import com.gemmaagent.app.tools.Calculator
import com.gemmaagent.app.util.BitmapUtil
import com.gemmaagent.app.util.Ocr
import com.gemmaagent.app.web.WebResult
import com.gemmaagent.app.web.WebSearchEngine
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Status jawaban yang sedang dialirkan (diperbarui maksimal tiap 50 ms). */
data class StreamState(
    val messageId: Long,
    val text: String = "",
    val thought: String = "",
    val phase: String = "",
    val steps: List<String> = emptyList(),
    val compareFast: String? = null,
    /** Sumber web (judul, url) yang sudah ditemukan — ditampilkan sebagai karusel saat jawaban masih ditulis. */
    val sources: List<Pair<String, String>> = emptyList(),
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

/** Konteks mesin untuk satu permintaan. */
private data class EngineCtx(val model: ModelFile, val settings: ModelSettings, val threads: Int)

class ChatController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val db: AppDatabase,
    private val kb: KnowledgeDb,
    private val llm: UnifiedLlmEngine,
    private val settings: SettingsRepository,
    private val models: ModelRepository,
    private val modelSettings: ModelSettingsStore,
    private val web: WebSearchEngine,
) {
    private val roomStore = RoomMessageStore(db.messages())
    private val memoryStore = MemoryMessageStore()

    private val _incognito = MutableStateFlow(false)
    val incognito: StateFlow<Boolean> = _incognito.asStateFlow()
    private val store: MessageStore get() = if (_incognito.value) memoryStore else roomStore

    private val _sessionId = MutableStateFlow(settings.lastSessionId())
    val sessionId: StateFlow<Long?> = _sessionId.asStateFlow()

    val sessions: Flow<List<SessionEntity>> = db.sessions().observeAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: Flow<List<MessageEntity>> = combine(_sessionId, _incognito) { id, inc -> id to inc }.flatMapLatest { (id, inc) ->
        when {
            inc -> memoryStore.observe(INCOGNITO_ID)
            id == null -> flowOf(emptyList())
            else -> roomStore.observe(id)
        }
    }

    val starred: Flow<List<MessageEntity>> = db.messages().observeStarred()
    val notes = db.notes().observeAll()
    val memories = db.memories().observeAll()
    val webArchive = kb.dao().observeWeb()
    val kbDocs = kb.dao().observeDocs()

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

    // ---------- Token buffering 50 ms ----------
    @Volatile private var lastEmit = 0L

    private fun streamUpdate(state: StreamState, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastEmit >= 50) {
            lastEmit = now
            _stream.value = state
        }
    }

    init {
        scope.launch {
            val id = _sessionId.value
            if (id != null && db.sessions().get(id) == null) setSession(null)
        }
    }

    private fun emit(msg: String) {
        _events.tryEmit(msg)
    }

    private fun engineCtx(): EngineCtx? {
        val m = models.active.value ?: return null
        return EngineCtx(m, modelSettings.get(m.path, m.type), settings.current.cpuThreads)
    }

    /** Muat model aktif di latar (Lazy: hanya bila ada model). */
    fun preload() {
        val e = engineCtx()
        if (e == null) llm.markNoModel() else llm.loadAsync(e.model, e.settings, e.threads)
    }

    private fun cancelFollowUps() {
        if (followJob?.isActive == true) {
            llm.stop()
            followJob?.cancel()
        }
    }

    // ============================== SESI ==============================

    fun setSession(id: Long?) {
        if (_incognito.value) exitIncognito()
        _sessionId.value = id
        settings.setLastSessionId(id)
    }

    fun newSession() {
        if (_generating.value) stop()
        setSession(null)
    }

    /** Sesi Incognito: pesan hanya di RAM, tidak masuk riwayat/memori, hilang saat keluar. */
    fun startIncognito() {
        if (_generating.value) stop()
        memoryStore.clear()
        _incognito.value = true
        _sessionId.value = INCOGNITO_ID
    }

    private fun exitIncognito() {
        memoryStore.clear()
        _incognito.value = false
        _sessionId.value = null
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
        if (_incognito.value) return INCOGNITO_ID
        _sessionId.value?.let { return it }
        val title = firstText.lineSequence().firstOrNull().orEmpty().trim().take(42).ifBlank { "Percakapan baru" }
        val id = db.sessions().insert(SessionEntity(title = title))
        _sessionId.value = id
        settings.setLastSessionId(id)
        return id
    }

    private suspend fun touch(sid: Long) {
        if (sid != INCOGNITO_ID) db.sessions().touch(sid)
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

                if (imageUri == null && document == null) {
                    Calculator.tryHandle(trimmed)?.let { r ->
                        store.insert(MessageEntity(sessionId = sid, role = Roles.USER, text = trimmed))
                        store.insert(MessageEntity(sessionId = sid, role = Roles.ASSISTANT, kind = Kinds.CALC,
                            text = "🧮 **${r.value}**\n\n`${r.expression}` — dihitung oleh kalkulator lokal (bukan AI)."))
                        touch(sid)
                        return@launch
                    }
                }

                if (settings.current.useMemory && !_incognito.value) {
                    MemoryExtractor.extract(trimmed).forEach { fact ->
                        if (db.memories().countSame(fact) == 0) {
                            db.memories().insert(MemoryEntity(fact = fact))
                            emit("🧠 Diingat: $fact")
                        }
                    }
                }

                // OCR offline (ML Kit) untuk gambar.
                var ocrText: String? = null
                var imagePath: String? = null
                if (imageUri != null) {
                    // Dekode SEKALI (maks 1024×1024) di Dispatchers.IO, dipakai untuk thumbnail & OCR.
                    val bmp = withContext(Dispatchers.IO) { runCatching { BitmapUtil.decodeDownsampled(context, imageUri) }.getOrNull() }
                    if (bmp == null) {
                        emit("Gambar tidak bisa dibuka.")
                    } else {
                        try {
                            imagePath = withContext(Dispatchers.IO) { runCatching { ImageStore.save(context, bmp) }.getOrNull() }
                            ocrText = withContext(Dispatchers.Default) {
                                runCatching { Ocr.read(bmp) }.getOrElse { emit("OCR gagal: ${it.message}"); "" }
                            }
                        } finally {
                            bmp.recycle()
                        }
                        if (ocrText.isNullOrBlank()) emit("Tidak ada teks terbaca pada gambar.")
                    }
                }

                val userMsg = MessageEntity(
                    sessionId = sid,
                    role = Roles.USER,
                    text = trimmed.ifEmpty { if (imageUri != null) "Jelaskan teks pada gambar ini." else "Rangkum dokumen ini." },
                    imagePath = imagePath,
                    attachmentName = document?.name ?: imagePath?.let { "Gambar (OCR)" },
                    attachmentText = document?.text ?: ocrText?.takeIf { it.isNotBlank() }?.let { OCR_PREFIX + it },
                )
                val id = store.insert(userMsg)
                touch(sid)
                respond(userMsg.copy(id = id), compare, keepVersionsOf = null)
            } catch (t: Throwable) {
                emit("Gagal mengirim: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    /** Regenerasi (fitur 29: versi jawaban lama tetap disimpan sebagai cabang). */
    fun regenerate(assistant: MessageEntity) {
        if (_generating.value) return
        cancelFollowUps()
        scope.launch {
            val list = store.list(assistant.sessionId)
            val user = list.lastOrNull { it.id < assistant.id && it.role == Roles.USER } ?: return@launch
            // Hapus pesan SETELAH jawaban ini; jawaban ini sendiri diperbarui dengan versi baru.
            list.filter { it.id > assistant.id }.forEach { store.delete(it.id) }
            respond(user, compare = assistant.kind == Kinds.COMPARE, keepVersionsOf = assistant)
        }
    }

    /** Pindah versi jawaban (‹ 1/3 ›). */
    fun selectVersion(msg: MessageEntity, index: Int) = scope.launch {
        val payload = runCatching { JSONObject(msg.payload) }.getOrNull() ?: return@launch
        val versions = payload.optJSONArray("versions") ?: return@launch
        val v = versions.optJSONObject(index) ?: return@launch
        payload.put("active", index)
        store.update(msg.copy(text = v.optString("text"), thought = v.optString("thought"),
            tokensPerSec = v.optDouble("tps", 0.0), payload = payload.toString()))
    }

    fun editAndResend(user: MessageEntity, newText: String) {
        if (_generating.value || newText.isBlank()) return
        cancelFollowUps()
        scope.launch {
            store.deleteFrom(user.sessionId, user.id)
            val edited = user.copy(id = 0, text = newText.trim(), createdAt = System.currentTimeMillis())
            val id = store.insert(edited)
            respond(edited.copy(id = id), compare = false, keepVersionsOf = null)
        }
    }

    fun stop() {
        llm.stop()
        job?.cancel()
        followJob?.cancel()
    }

    private fun respond(user: MessageEntity, compare: Boolean, keepVersionsOf: MessageEntity?) {
        job = scope.launch {
            _generating.value = true
            val placeholder = keepVersionsOf?.copy(text = "", thought = "")
                ?: MessageEntity(sessionId = user.sessionId, role = Roles.ASSISTANT, text = "", kind = if (compare) Kinds.COMPARE else Kinds.TEXT)
            val aid = if (keepVersionsOf != null) keepVersionsOf.id else store.insert(placeholder)
            val oldPayload = keepVersionsOf?.let { runCatching { JSONObject(it.payload) }.getOrNull() }
            var steps = listOf<String>()
            streamUpdate(StreamState(aid, phase = "Menyiapkan…"), force = true)
            var citations: List<WebResult> = emptyList()
            var usedOnline = false
            var searchQuery: String? = null
            var fromCache = false
            fun liveState(text: String = "", thought: String = "", phase: String = "", steps: List<String> = emptyList(), compareFast: String? = null) =
                StreamState(aid, text, thought, phase, steps, compareFast, citations.map { it.title to it.url })
            try {
                val s = settings.current
                val ec = engineCtx() ?: throw IllegalStateException("Belum ada model. Buka Model Hub untuk memilih file .gguf / .litertlm.")
                val ms = ec.settings
                val outputReserve = minOf(ms.maxTokens, ms.nCtx / 2)
                val budgetChars = ((ms.nCtx - outputReserve - 150) * 2.8).toInt().coerceAtLeast(600)

                var prompt = user.text
                val attach = user.attachmentText
                if (attach != null && attach.startsWith(OCR_PREFIX)) {
                    prompt = PromptBuilder.withOcr(user.text, attach.removePrefix(OCR_PREFIX).take((budgetChars * 0.7).toInt()))
                } else if (attach != null) {
                    prompt = buildDocumentPrompt(ec, user, attach, budgetChars, aid)
                } else if (s.online) {
                    val url = WebSearchEngine.findUrl(user.text)
                    if (!web.isOnline()) {
                        // Auto-fallback offline: coba arsip, lalu pengetahuan model.
                        val cached = web.searchCache(user.text)
                        if (cached.isNotEmpty()) {
                            citations = cached; fromCache = true; usedOnline = true
                            steps = listOf("📴 Internet mati — memakai arsip pencarian offline (${cached.size} artikel)")
                            prompt = PromptBuilder.withWebContext(user.text, cached, (budgetChars * 0.6).toInt(), fromCache = true)
                        } else emit("📴 Internet tidak tersedia — menjawab dengan pengetahuan offline Gemma.")
                    } else if (url != null) {
                        steps = listOf("🔗 Membaca tautan: ${WebSearchEngine.hostOf(url)}")
                        streamUpdate(liveState(phase = steps.last(), steps = steps), force = true)
                        val art = runCatching { web.readArticle(url, (budgetChars * 0.8).toInt()) }.getOrNull()
                        if (art != null && art.snippet.length > 80) {
                            citations = listOf(WebResult(art.title, url, ""))
                            prompt = PromptBuilder.urlSummary(user.text.replace(url, "").trim(), art.title, art.snippet)
                            usedOnline = true
                            steps = steps + "✅ Artikel terbaca (${art.snippet.length} karakter)"
                        } else {
                            steps = steps + "⚠️ Tautan tidak bisa dibaca"
                            emit("Tautan tidak bisa dibaca — menjawab tanpa isi tautan.")
                        }
                    } else {
                        searchQuery = user.text.take(200)
                        val ctx = runCatching {
                            web.searchAndRead(user.text, s.searchSource, (budgetChars * 0.65).toInt()) { st ->
                                steps = steps + st
                                streamUpdate(liveState(phase = "🔎 $st", steps = steps), force = true)
                            }
                        }.getOrNull()
                        if (ctx != null && ctx.results.isNotEmpty()) {
                            citations = ctx.results
                            prompt = PromptBuilder.withWebContext(user.text, ctx.results, (budgetChars * 0.65).toInt())
                            usedOnline = true
                        } else {
                            val cached = web.searchCache(user.text)
                            if (cached.isNotEmpty()) {
                                citations = cached; fromCache = true; usedOnline = true
                                steps = steps + "Memakai arsip pencarian offline"
                                prompt = PromptBuilder.withWebContext(user.text, cached, (budgetChars * 0.6).toInt(), fromCache = true)
                            } else emit("Pencarian web gagal — menjawab dengan pengetahuan offline Gemma.")
                        }
                    }
                }

                // Basis Pengetahuan Lokal (RAG FTS).
                if (s.useKnowledge && attach == null && !usedOnline) {
                    val match = KnowledgeDb.ftsQuery(user.text)
                    if (match != null) {
                        val hits = runCatching { kb.dao().searchChunks(match, 4) }.getOrDefault(emptyList())
                        if (hits.isNotEmpty()) {
                            steps = steps + "📚 ${hits.size} kutipan dari basis pengetahuan"
                            prompt = PromptBuilder.withKnowledge(user.text, hits, (budgetChars * 0.6).toInt())
                        }
                    }
                }

                val history = buildHistory(user, s.historyTurns, budgetChars - prompt.length)
                val memories = if (s.useMemory && !_incognito.value) db.memories().all().map { it.fact } else emptyList()
                val citeJson = JSONArray().apply { citations.forEach { put(JSONObject().put("title", it.title).put("url", it.url)) } }
                val searchJson = if (steps.isNotEmpty()) JSONObject().put("query", searchQuery ?: "").put("steps", JSONArray(steps)).put("cache", fromCache) else null

                if (compare) {
                    streamUpdate(liveState(phase = "⚡ Mode Cepat…", steps = steps), force = true)
                    val fast = llm.generate(ec.model, ms, ec.threads, GenRequest(
                        PromptBuilder.systemPrompt(s.personaId, s.customSystemPrompt, false, memories, usedOnline), history, prompt, thinking = false,
                    )) { t, th -> streamUpdate(liveState(t, th, "⚡ Mode Cepat", steps)) }
                    if (fast.stopped) throw CancellationException("stop")
                    val deep = llm.generate(ec.model, ms, ec.threads, GenRequest(
                        PromptBuilder.systemPrompt(s.personaId, s.customSystemPrompt, true, memories, usedOnline), history, prompt, thinking = true,
                    )) { t, th -> streamUpdate(liveState(t, th, "🧠 Mode Mendalam", steps, compareFast = fast.text)) }
                    val payload = JSONObject().put("fast", resultJson(fast)).put("deep", resultJson(deep)).put("citations", citeJson)
                    searchJson?.let { payload.put("search", it) }
                    store.update(placeholder.copy(id = aid, text = fast.text.ifBlank { deep.text }, thought = deep.thought, payload = payload.toString(),
                        online = usedOnline, tokensPerSec = deep.tokensPerSec, tokenCount = fast.tokens + deep.tokens, durationMs = fast.durationMs + deep.durationMs))
                } else {
                    val system = PromptBuilder.systemPrompt(s.personaId, s.customSystemPrompt, s.thinking, memories, usedOnline)
                    streamUpdate(liveState(phase = if (s.thinking) "🧠 Berpikir…" else "✍️ Menulis…", steps = steps), force = true)
                    val r = llm.generate(ec.model, ms, ec.threads, GenRequest(system, history, prompt, thinking = s.thinking)) { t, th ->
                        streamUpdate(liveState(t, th, if (s.thinking && t.isBlank()) "🧠 Berpikir…" else "✍️ Menulis…", steps))
                    }
                    val text = when {
                        r.stopped -> r.text + "\n\n_(dihentikan)_"
                        r.loopDetected -> r.text + "\n\n_(dihentikan otomatis: model mulai mengulang teks yang sama)_"
                        r.text.isBlank() -> "_(Model tidak menghasilkan jawaban. Coba Regenerasi, ganti Chat Template di Pengaturan Model, atau mulai Sesi Baru.)_"
                        else -> r.text
                    }
                    val payload = JSONObject().put("citations", citeJson)
                    searchJson?.let { payload.put("search", it) }
                    if (r.reusedTokens > 0) payload.put("reused", r.reusedTokens)
                    // Cabang versi jawaban.
                    val versions = oldPayload?.optJSONArray("versions") ?: keepVersionsOf?.let { old ->
                        JSONArray().put(JSONObject().put("text", old.text).put("thought", old.thought).put("tps", old.tokensPerSec))
                    }
                    if (versions != null) {
                        versions.put(JSONObject().put("text", text).put("thought", r.thought).put("tps", r.tokensPerSec))
                        payload.put("versions", versions).put("active", versions.length() - 1)
                    }
                    store.update(placeholder.copy(id = aid, kind = Kinds.TEXT, text = text, thought = r.thought, payload = payload.toString(),
                        online = usedOnline, tokensPerSec = r.tokensPerSec, tokenCount = r.tokens, durationMs = r.durationMs))
                    if (s.followUps && !r.stopped && r.text.isNotBlank()) launchFollowUps(ec, aid, user.text, r.text)
                }
                touch(user.sessionId)
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    val partial = _stream.value?.text.orEmpty()
                    store.update(placeholder.copy(id = aid, text = partial + "\n\n_(dihentikan)_", payload = oldPayload?.toString() ?: ""))
                }
            } catch (t: Throwable) {
                store.update(placeholder.copy(id = aid, kind = Kinds.ERROR, text = "⚠️ ${t.message ?: t.javaClass.simpleName}"))
            } finally {
                _stream.value = null
                _generating.value = false
            }
        }
    }

    private fun resultJson(r: GenResult) = JSONObject().put("text", r.text).put("thought", r.thought).put("tps", r.tokensPerSec).put("tokens", r.tokens)

    private suspend fun buildDocumentPrompt(ec: EngineCtx, user: MessageEntity, doc: String, budgetChars: Int, aid: Long): String {
        val name = user.attachmentName ?: "dokumen"
        val question = user.text
        val room = (budgetChars - question.length - 200).coerceAtLeast(500)
        if (doc.length <= room) return PromptBuilder.withDocument(question, name, doc)
        val chunks = DocumentReader.chunk(doc, minOf(room, DocumentReader.MAX_CHUNK_CHARS)).take(MAX_CHUNKS)
        val summaries = StringBuilder()
        chunks.forEachIndexed { i, c ->
            val phase = "📄 Merangkum bagian ${i + 1}/${chunks.size}…"
            streamUpdate(StreamState(aid, phase = phase), force = true)
            val r = llm.generate(ec.model, ec.settings, ec.threads, GenRequest(
                "Kamu perangkum dokumen yang teliti. Jawab dalam Bahasa Indonesia.", emptyList(),
                PromptBuilder.chunkSummary(name, i + 1, chunks.size, c), temperature = 0.2f, maxOutputTokens = 220,
            )) { t, _ -> streamUpdate(StreamState(aid, t, "", phase)) }
            if (r.stopped) throw CancellationException("stop")
            summaries.append("Bagian ${i + 1}:\n").append(r.text.take(room / chunks.size + 200)).append("\n")
        }
        if (doc.length > chunks.sumOf { it.length }) emit("Dokumen sangat panjang: hanya ${chunks.size} bagian pertama yang dirangkum.")
        return PromptBuilder.withDocument(question, "$name (ringkasan per bagian)", summaries.toString().take(room))
    }

    private suspend fun buildHistory(user: MessageEntity, turns: Int, budgetChars: Int): List<ChatTurn> {
        val all = store.list(user.sessionId).filter { it.id < user.id && it.kind != Kinds.ERROR && it.text.isNotBlank() }
        val window = all.takeLast(turns * 2).map {
            ChatTurn(it.role == Roles.USER, it.text.replace("_(dihentikan)_", "").trim().take(700))
        }.toMutableList()
        while (window.isNotEmpty() && window.sumOf { it.text.length } > budgetChars.coerceAtLeast(0)) window.removeAt(0)
        while (window.isNotEmpty() && !window.first().user) window.removeAt(0)
        return window
    }

    private fun launchFollowUps(ec: EngineCtx, aid: Long, question: String, answer: String) {
        followJob = scope.launch {
            try {
                val r = llm.generate(ec.model, ec.settings, ec.threads, GenRequest(
                    "Kamu membantu menyarankan pertanyaan lanjutan. Bahasa Indonesia.", emptyList(),
                    PromptBuilder.followUps(question, answer), temperature = 0.6f, maxOutputTokens = 80,
                )) { _, _ -> }
                if (r.stopped) return@launch
                val list = r.text.lines()
                    .map { it.trim().trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim() }
                    .filter { it.length in 4..90 }.distinct().take(3)
                if (list.isEmpty()) return@launch
                val msg = store.get(aid) ?: return@launch
                val payload = runCatching { JSONObject(msg.payload) }.getOrDefault(JSONObject())
                payload.put("followups", JSONArray(list))
                store.setPayload(aid, payload.toString())
            } catch (_: Throwable) {
            }
        }
    }

    // ============================== RANTAI PROMPT (fitur 22) ==============================

    fun runChain(chain: PromptChain, input: String) {
        if (_generating.value || chain.steps.isEmpty() || input.isBlank()) return
        cancelFollowUps()
        job = scope.launch {
            _generating.value = true
            var aid = -1L
            try {
                val ec = engineCtx() ?: throw IllegalStateException("Belum ada model.")
                val sid = ensureSession("${chain.emoji} ${chain.title}")
                store.insert(MessageEntity(sessionId = sid, role = Roles.USER, text = "${chain.emoji} **Rantai: ${chain.title}**\n\n${input.trim()}"))
                var prev = input.trim()
                chain.steps.forEachIndexed { i, (title, tmpl) ->
                    aid = store.insert(MessageEntity(sessionId = sid, role = Roles.ASSISTANT, text = ""))
                    val phase = "🔗 Langkah ${i + 1}/${chain.steps.size}: $title"
                    streamUpdate(StreamState(aid, phase = phase), force = true)
                    val prompt = tmpl.replace("{input}", input.trim().take(4000)).replace("{prev}", prev.take(3000))
                    val r = llm.generate(ec.model, ec.settings, ec.threads, GenRequest(
                        PromptBuilder.systemPrompt(settings.current.personaId, settings.current.customSystemPrompt, false, emptyList(), false),
                        emptyList(), prompt,
                    )) { t, th -> streamUpdate(StreamState(aid, t, th, phase)) }
                    store.get(aid)?.let { store.update(it.copy(text = "**🔗 Langkah ${i + 1}: $title**\n\n" + r.text, tokensPerSec = r.tokensPerSec, tokenCount = r.tokens, durationMs = r.durationMs)) }
                    if (r.stopped) throw CancellationException("stop")
                    prev = r.text
                }
                touch(sid)
            } catch (e: CancellationException) {
                if (aid > 0) withContext(NonCancellable) { store.get(aid)?.let { store.update(it.copy(text = (_stream.value?.text ?: it.text) + "\n\n_(dihentikan)_")) } }
            } catch (t: Throwable) {
                emit("Rantai gagal: ${t.message}")
            } finally {
                _stream.value = null
                _generating.value = false
            }
        }
    }

    // ============================== ALAT BELAJAR ==============================

    fun makeStudy(source: MessageEntity, quiz: Boolean) {
        if (_generating.value) return
        cancelFollowUps()
        job = scope.launch {
            _generating.value = true
            val aid = store.insert(MessageEntity(sessionId = source.sessionId, role = Roles.ASSISTANT, text = "", kind = if (quiz) Kinds.QUIZ else Kinds.FLASHCARDS))
            val phase = if (quiz) "📝 Membuat kuis…" else "🃏 Membuat flashcard…"
            streamUpdate(StreamState(aid, phase = phase), force = true)
            try {
                val ec = engineCtx() ?: throw IllegalStateException("Belum ada model.")
                val r = llm.generate(ec.model, ec.settings, ec.threads, GenRequest(
                    "Kamu pembuat materi belajar. Balas hanya JSON valid.", emptyList(),
                    if (quiz) PromptBuilder.quiz(source.text) else PromptBuilder.flashcards(source.text), temperature = 0.3f, maxOutputTokens = 700,
                )) { t, _ -> streamUpdate(StreamState(aid, t, "", phase)) }
                val json = extractJson(r.text)
                val ok = json != null && (if (quiz) json.optJSONArray("questions") else json.optJSONArray("cards"))?.length()?.let { it > 0 } == true
                val msg = store.get(aid) ?: return@launch
                if (ok) store.update(msg.copy(text = if (quiz) "📝 Kuis siap — pilih jawabanmu." else "🃏 Flashcard siap — ketuk kartu untuk membalik.",
                    payload = json.toString(), tokensPerSec = r.tokensPerSec, tokenCount = r.tokens))
                else store.update(msg.copy(kind = Kinds.TEXT, text = r.text.ifBlank { "Gagal membuat materi." } + "\n\n_(Format JSON tidak valid, ditampilkan sebagai teks.)_"))
            } catch (e: CancellationException) {
                withContext(NonCancellable) { store.delete(aid) }
            } catch (t: Throwable) {
                store.get(aid)?.let { store.update(it.copy(kind = Kinds.ERROR, text = "⚠️ ${t.message}")) }
            } finally {
                _stream.value = null
                _generating.value = false
            }
        }
    }

    fun makeChecklist(source: MessageEntity) = scope.launch {
        val items = source.text.lines().map { it.trim() }
            .filter { Regex("^([-*•]|\\d+[.)]|- \\[[ xX]])\\s+").containsMatchIn(it) }
            .map { it.replace(Regex("^([-*•]|\\d+[.)])\\s+"), "").replace(Regex("^\\[[ xX]]\\s*"), "").replace("**", "").trim() }
            .filter { it.isNotBlank() }
        val finalItems = items.ifEmpty { source.text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.length > 8 }.take(10) }
        if (finalItems.isEmpty()) { emit("Tidak ada daftar yang bisa dijadikan checklist."); return@launch }
        val arr = JSONArray()
        finalItems.take(25).forEach { arr.put(JSONObject().put("t", it).put("d", false)) }
        store.insert(MessageEntity(sessionId = source.sessionId, role = Roles.ASSISTANT, kind = Kinds.CHECKLIST,
            text = "✅ Daftar tugas (${finalItems.size.coerceAtMost(25)} item)", payload = JSONObject().put("items", arr).toString()))
    }

    fun toggleChecklist(msg: MessageEntity, index: Int) = scope.launch {
        val json = runCatching { JSONObject(msg.payload) }.getOrNull() ?: return@launch
        val item = json.optJSONArray("items")?.optJSONObject(index) ?: return@launch
        item.put("d", !item.optBoolean("d"))
        store.setPayload(msg.id, json.toString())
    }

    fun defineWord(word: String, sentence: String) {
        if (_generating.value) { emit("Tunggu jawaban selesai dulu untuk membuka kamus."); return }
        val ec = engineCtx() ?: run { emit("Belum ada model."); return }
        cancelFollowUps()
        _define.value = DefineState.Loading(word)
        scope.launch {
            _generating.value = true
            try {
                val r = llm.generate(ec.model, ec.settings, ec.threads,
                    GenRequest("Kamu kamus bahasa yang ringkas.", emptyList(), PromptBuilder.define(word, sentence), 0.2f, 120)) { t, _ ->
                    if (_define.value != null) _define.value = DefineState.Result(word, t)
                }
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

    // ============================== BASIS PENGETAHUAN (fitur 19) ==============================

    fun addToKnowledge(doc: LoadedDocument) = scope.launch(Dispatchers.IO) {
        try {
            val chunks = DocumentReader.chunk(doc.text, 900)
            val id = kb.dao().insertDoc(KbDocEntity(name = doc.name, chars = doc.text.length, chunks = chunks.size))
            kb.dao().insertChunks(chunks.map { KbChunkFts(id.toString(), doc.name, it) })
            emit("📚 \"${doc.name}\" diindeks (${chunks.size} potongan).")
        } catch (t: Throwable) {
            emit("Gagal mengindeks: ${t.message}")
        }
    }

    fun deleteKnowledgeDoc(id: Long) = scope.launch {
        kb.dao().deleteChunks(id.toString())
        kb.dao().deleteDoc(id)
    }

    fun deleteWebCache(id: Long) = scope.launch { kb.dao().deleteWeb(id) }
    fun clearWebCache() = scope.launch { kb.dao().clearWeb() }

    // ============================== FAVORIT, CATATAN, MEMORI ==============================

    fun toggleStar(m: MessageEntity) = scope.launch {
        if (m.sessionId == INCOGNITO_ID) { emit("Pesan incognito tidak bisa dibintangi."); return@launch }
        db.messages().setStarred(m.id, !m.starred)
    }

    fun saveNote(m: MessageEntity) = scope.launch {
        val title = m.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().replace(Regex("[#*`>]"), "").trim().take(60)
        db.notes().insert(NoteEntity(title = title.ifBlank { "Catatan" }, text = m.text, sourceMessageId = m.id.takeIf { m.sessionId != INCOGNITO_ID }))
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

    private fun extractJson(raw: String): JSONObject? {
        val s = raw.replace("```json", "").replace("```", "")
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(s.substring(start, end + 1)) }.getOrNull()
    }

    companion object {
        const val INCOGNITO_ID = -1L
        private const val MAX_CHUNKS = 8
        private const val OCR_PREFIX = "\u0000OCR\u0000"
    }
}

/** Menyimpan gambar (sudah ≤ 1024 px) sebagai JPEG untuk ditampilkan di gelembung chat. Panggil dari Dispatchers.IO. */
object ImageStore {
    fun save(context: Context, bmp: android.graphics.Bitmap): String {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
        return file.absolutePath
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
