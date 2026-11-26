package com.gemmaagent.app.llm

import android.content.Context
import com.gemmaagent.app.data.ModelFile
import com.gemmaagent.app.util.CrashLogger
import com.gemmaagent.app.util.DeviceInfo
import com.gemmaagent.app.util.Fmt
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.RepetitionPenaltyConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.Executors

enum class ModelStatus(val label: String) {
    NO_MODEL("Belum ada model"),
    NOT_LOADED("Belum dimuat"),
    UNLOADING("Melepas model…"),
    LOADING("Memuat…"),
    READY("Siap"),
    BUSY("Menjawab…"),
    ERROR("Error"),
}

data class ChatTurn(val user: Boolean, val text: String)

data class GenRequest(
    val systemPrompt: String,
    val history: List<ChatTurn>,
    val userText: String,
    /** null = pakai pengaturan model. */
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val thinking: Boolean = false,
)

data class GenResult(
    val text: String,
    val thought: String,
    val tokens: Int,
    val tokensPerSec: Double,
    val durationMs: Long,
    val stopped: Boolean,
    val promptTokens: Int = 0,
    val reusedTokens: Int = 0,
    val loopDetected: Boolean = false,
)

data class BenchResult(val ppTokensPerSec: Double, val tgTokensPerSec: Double)

/** Hasil mentah dari satu mesin. */
private data class RawResult(
    val text: String,
    val channelThought: String,
    val tokens: Int,
    val promptTokens: Int,
    val reused: Int,
    val decodeMs: Long,
    val stopped: Boolean,
)

private interface ChatEngine {
    fun load(path: String, s: ModelSettings, threads: Int, onProgress: (Float) -> Unit)
    fun unload()
    suspend fun generate(req: GenRequest, s: ModelSettings, onPiece: (piece: String, channel: String) -> Boolean): RawResult
    fun stop()
    fun clearCache()
    fun bench(): BenchResult?
    fun info(): String?
}

// =====================================================================
// llama.cpp (GGUF)
// =====================================================================
private class LlamaCppEngine : ChatEngine {
    override fun load(path: String, s: ModelSettings, threads: Int, onProgress: (Float) -> Unit) {
        if (!LlamaNative.ensureLibrary()) throw IllegalStateException("Pustaka native llama.cpp gagal dimuat di HP ini.")
        val code = LlamaNative.nativeLoad(path, s.nCtx, threads) { p -> onProgress(p); true }
        when (code) {
            0 -> Unit
            1 -> throw IllegalStateException("File GGUF tidak bisa dimuat (rusak, belum selesai diunduh, atau arsitektur belum didukung).")
            else -> throw IllegalStateException("Konteks ${s.nCtx} token gagal dibuat — RAM tidak cukup. Kecilkan n_ctx di Pengaturan Model.")
        }
    }

    override fun unload() {
        if (LlamaNative.ensureLibrary()) LlamaNative.nativeUnload()
    }

    override suspend fun generate(req: GenRequest, s: ModelSettings, onPiece: (String, String) -> Boolean): RawResult {
        val prompt = buildPrompt(req, s)
        val out = StringBuilder()
        val stats = LlamaNative.nativeGenerate(
            prompt.toByteArray(Charsets.UTF_8),
            req.temperature ?: s.temperature, s.topP, s.topK, s.repeatPenalty,
            req.maxOutputTokens ?: s.maxTokens,
        ) { bytes ->
            val piece = String(bytes, Charsets.UTF_8)
            out.append(piece)
            onPiece(piece, "")
        }
        val status = stats[5]
        if (status < 0) throw IllegalStateException("llama.cpp gagal memproses (kode $status). Coba Bersihkan Cache RAM atau kecilkan n_ctx.")
        return RawResult(
            text = out.toString(), channelThought = "", tokens = stats[2].toInt(),
            promptTokens = stats[0].toInt(), reused = stats[1].toInt(), decodeMs = stats[4] / 1000,
            stopped = status == 1L,
        )
    }

    private fun buildPrompt(req: GenRequest, s: ModelSettings): String {
        if (s.template == ChatTemplate.AUTO) {
            val roles = mutableListOf<ByteArray>()
            val contents = mutableListOf<ByteArray>()
            if (req.systemPrompt.isNotBlank()) { roles += "system".toByteArray(); contents += req.systemPrompt.toByteArray(Charsets.UTF_8) }
            req.history.forEach { roles += (if (it.user) "user" else "assistant").toByteArray(); contents += it.text.toByteArray(Charsets.UTF_8) }
            roles += "user".toByteArray(); contents += req.userText.toByteArray(Charsets.UTF_8)
            val formatted = runCatching { LlamaNative.nativeFormatChat(roles.toTypedArray(), contents.toTypedArray(), req.thinking) }.getOrNull()
            if (formatted != null && formatted.isNotEmpty()) return String(formatted, Charsets.UTF_8)
        }
        return PromptFormatter.format(s.template, req.systemPrompt, req.history, req.userText)
    }

    override fun stop() {
        if (LlamaNative.ensureLibrary()) LlamaNative.nativeStop()
    }

    override fun clearCache() {
        if (LlamaNative.ensureLibrary()) LlamaNative.nativeClearCache()
    }

    override fun bench(): BenchResult? {
        val r = LlamaNative.nativeBench(128, 32)
        return BenchResult(r[0], r[1])
    }

    override fun info(): String? = LlamaNative.nativeInfo()?.let { String(it, Charsets.UTF_8) }
}

// =====================================================================
// LiteRT-LM (.litertlm / .task)
// =====================================================================
private class LiteRtEngine(private val context: Context) : ChatEngine {
    private var engine: Engine? = null

    @Volatile
    private var conversation: Conversation? = null

    override fun load(path: String, s: ModelSettings, threads: Int, onProgress: (Float) -> Unit) {
        onProgress(0.1f)
        val config = EngineConfig(
            modelPath = path,
            backend = Backend.CPU(threadCount = threads),
            maxNumTokens = s.nCtx,
            cacheDir = context.cacheDir.absolutePath,
        )
        val e = Engine(config)
        try {
            e.initialize()
        } catch (t: Throwable) {
            runCatching { e.close() }
            throw t
        }
        engine = e
        onProgress(1f)
    }

    override fun unload() {
        runCatching { engine?.close() }
        engine = null
    }

    override suspend fun generate(req: GenRequest, s: ModelSettings, onPiece: (String, String) -> Boolean): RawResult {
        val eng = engine ?: throw IllegalStateException("Model belum dimuat")
        val initial = req.history.map { if (it.user) Message.user(it.text) else Message.model(it.text) }
        val sampler = SamplerConfig(topK = s.topK, topP = s.topP.toDouble(), temperature = (req.temperature ?: s.temperature).toDouble())
        fun cfg(withThinking: Boolean) = ConversationConfig(
            systemInstruction = Contents.of(req.systemPrompt),
            initialMessages = initial,
            samplerConfig = sampler,
            maxOutputToken = req.maxOutputTokens ?: s.maxTokens,
            thinkingConfig = if (withThinking) ThinkingConfig(enableThinking = req.thinking) else null,
        )
        val conv = try {
            eng.createConversation(cfg(true))
        } catch (_: Throwable) {
            eng.createConversation(cfg(false))
        }
        conversation = conv
        var tokens = 0
        var first = 0L
        var stopped = false
        val text = StringBuilder()
        val thought = StringBuilder()
        try {
            conv.sendMessageAsync(
                Contents.of(req.userText),
                repetitionPenaltyConfig = RepetitionPenaltyConfig(repetitionPenalty = s.repeatPenalty),
            ).collect { msg ->
                val piece = msg.toString()
                val ch = msg.channels.values.joinToString("")
                if (piece.isNotEmpty() || ch.isNotEmpty()) {
                    if (first == 0L) first = System.currentTimeMillis()
                    tokens++
                    text.append(piece)
                    thought.append(ch)
                    if (!onPiece(piece, ch) && !stopped) {
                        stopped = true
                        runCatching { conv.cancelProcess() }
                    }
                }
            }
        } catch (t: Throwable) {
            if (!stopped) {
                val m = (t.message ?: "").lowercase()
                if (!("max" in m && "token" in m)) throw t
            }
        } finally {
            conversation = null
            runCatching { conv.close() }
        }
        val decodeMs = if (first > 0) System.currentTimeMillis() - first else 0
        return RawResult(text.toString(), thought.toString(), tokens, 0, 0, decodeMs, stopped)
    }

    override fun stop() {
        runCatching { conversation?.cancelProcess() }
    }

    override fun clearCache() {
        runCatching { context.cacheDir.listFiles()?.filter { it.name.endsWith(".cache") || it.name.contains("xnnpack") }?.forEach { it.deleteRecursively() } }
    }

    override fun bench(): BenchResult? = null
    override fun info(): String? = null
}

// =====================================================================
// UnifiedLlmEngine
// =====================================================================

/**
 * Mesin terpadu: llama.cpp untuk .gguf dan LiteRT-LM untuk .litertlm/.task.
 * - Strict Unload-Before-Load: model lama SELALU dilepas & memori dibersihkan sebelum model baru dimuat.
 * - Semua panggilan native di 1 thread khusus + Mutex.
 * - Semua error ditangkap dan diubah menjadi pesan yang mudah dipahami.
 */
class UnifiedLlmEngine(private val context: Context) {

    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "gemma-llm").apply { priority = Thread.MAX_PRIORITY - 1 } }
        .asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, _ -> })
    private val mutex = Mutex()

    private var current: ChatEngine? = null
    private var loadedKey: String? = null

    @Volatile
    private var stopRequested = false

    private val _status = MutableStateFlow(ModelStatus.NOT_LOADED)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Progres memuat model 0..1 (null bila tidak sedang memuat). */
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    private val _loaded = MutableStateFlow<ModelFile?>(null)
    val loaded: StateFlow<ModelFile?> = _loaded.asStateFlow()

    fun markNoModel() {
        if (current == null) _status.value = ModelStatus.NO_MODEL
    }

    private fun key(m: ModelFile, s: ModelSettings, threads: Int) = "${m.path}|${s.nCtx}|$threads"

    /** Muat model di latar (tombol "Muat / Pakai"). */
    fun loadAsync(model: ModelFile, s: ModelSettings, threads: Int) {
        scope.launch { mutex.withLock { runCatching { ensureLoaded(model, s, threads) } } }
    }

    private suspend fun ensureLoaded(model: ModelFile, s: ModelSettings, threads: Int) {
        val k = key(model, s, threads)
        if (current != null && loadedKey == k) return

        // ---- Strict Unload-Before-Load ----
        if (current != null) {
            _status.value = ModelStatus.UNLOADING
            runCatching { current?.unload() }
            current = null
            loadedKey = null
            _loaded.value = null
            System.gc()
            Runtime.getRuntime().gc()
            delay(400)
        }

        // Validasi header file SEBELUM masuk ke kode native (mencegah crash akibat file rusak/terpotong).
        ModelValidator.check(model.path, model.type)?.let { problem -> fail(problem, null) }
        _status.value = ModelStatus.LOADING
        _error.value = null
        _progress.value = 0f
        val (availMb, _) = runCatching { DeviceInfo.availableRamMb(context) }.getOrDefault(0L to 0L)
        val engine: ChatEngine = if (model.type == EngineType.GGUF) LlamaCppEngine() else LiteRtEngine(context)
        try {
            engine.load(model.path, s, threads) { p -> _progress.value = p.coerceIn(0f, 1f) }
            current = engine
            loadedKey = k
            _loaded.value = model
            _status.value = ModelStatus.READY
        } catch (t: Throwable) {
            runCatching { engine.unload() }
            current = null
            loadedKey = null
            _loaded.value = null
            val raw = (t.message ?: t.javaClass.simpleName).take(220)
            val lower = raw.lowercase()
            val msg = when {
                t is OutOfMemoryError || "memory" in lower || "alloc" in lower || "ram" in lower ->
                    "RAM tidak cukup untuk memuat ${model.name} (sisa ${Fmt.gbFromMb(availMb)} GB). Tutup aplikasi lain atau kecilkan n_ctx. Detail: $raw"
                else -> "Gagal memuat ${model.name}: $raw"
            }
            CrashLogger.record(context, "Gagal memuat model (aplikasi tidak ditutup)", t)
            fail(msg, t)
        } finally {
            _progress.value = null
        }
    }

    private fun fail(msg: String, t: Throwable?): Nothing {
        _status.value = ModelStatus.ERROR
        _error.value = msg
        throw IllegalStateException(msg, t)
    }

    /** Tombol "Lepas dari Memori (Unload)". */
    fun unloadAsync(onDone: () -> Unit = {}) {
        stop()
        scope.launch {
            mutex.withLock {
                _status.value = ModelStatus.UNLOADING
                runCatching { current?.unload() }
                current = null
                loadedKey = null
                _loaded.value = null
                System.gc()
                _status.value = ModelStatus.NOT_LOADED
            }
            onDone()
        }
    }

    fun unloadBlocking() {
        stop()
        runBlocking(dispatcher) {
            mutex.withLock {
                runCatching { current?.unload() }
                current = null
                loadedKey = null
                _loaded.value = null
                _status.value = ModelStatus.NOT_LOADED
            }
        }
    }

    /** Tombol Pembersih Cache RAM: lepas KV cache sementara. */
    fun clearCache(onDone: () -> Unit = {}) {
        stop()
        scope.launch {
            mutex.withLock {
                runCatching { current?.clearCache() }
                System.gc()
            }
            onDone()
        }
    }

    fun stop() {
        stopRequested = true
        runCatching { current?.stop() }
    }

    suspend fun generate(
        model: ModelFile,
        s: ModelSettings,
        threads: Int,
        req: GenRequest,
        onUpdate: (text: String, thought: String) -> Unit,
    ): GenResult {
        val job = scope.async {
            mutex.withLock {
                stopRequested = false
                ensureLoaded(model, s, threads)
                val eng = current ?: throw IllegalStateException("Model belum dimuat")
                _status.value = ModelStatus.BUSY
                // Pembersih output real-time + penjaga anti-looping.
                val cleaner = OutputCleaner(s.stopWords + if (model.type == EngineType.GGUF) PromptFormatter.defaultStops(s.template) else emptyList())
                val loopGuard = LoopGuard()
                val channel = StringBuilder()
                var loopCut: Int? = null
                val start = System.currentTimeMillis()
                val result = try {
                    eng.generate(req, s) { piece, ch ->
                        channel.append(ch)
                        val visible = cleaner.feed(piece)
                        if (loopCut == null) loopCut = loopGuard.check(visible)
                        val shown = loopCut?.let { visible.take(it) } ?: visible
                        val (t, th) = ThoughtSplitter.split(shown)
                        onUpdate(t, (channel.toString() + th).trim())
                        !cleaner.stopped && loopCut == null && !stopRequested
                    }
                } finally {
                    _status.value = ModelStatus.READY
                }
                val finalRaw = cleaner.finish().let { full -> loopCut?.let { full.take(it) } ?: full }
                val (text, th) = ThoughtSplitter.split(finalRaw)
                GenResult(
                    text = text.trim(),
                    thought = (result.channelThought + th).trim(),
                    tokens = result.tokens,
                    tokensPerSec = if (result.tokens > 1 && result.decodeMs > 0) result.tokens * 1000.0 / result.decodeMs else 0.0,
                    durationMs = System.currentTimeMillis() - start,
                    stopped = stopRequested && !cleaner.stopped && loopCut == null,
                    loopDetected = loopCut != null,
                    promptTokens = result.promptTokens,
                    reusedTokens = result.reused,
                )
            }
        }
        return try {
            job.await()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            val m = t.message ?: t.javaClass.simpleName
            throw IllegalStateException(if ("token" in m.lowercase() && "max" in m.lowercase()) "Teks terlalu panjang untuk n_ctx model. Mulai sesi baru atau naikkan n_ctx." else m, t)
        }
    }

    /** Benchmark mini (fitur 8). GGUF: pp & tg. LiteRT: tg dari jawaban pendek. */
    suspend fun bench(model: ModelFile, s: ModelSettings, threads: Int): BenchResult {
        if (model.type == EngineType.LITERT) {
            val r = generate(model, s, threads, GenRequest("", emptyList(), "Hitung dari 1 sampai 30 dipisah koma.", 0.1f, 96)) { _, _ -> }
            return BenchResult(0.0, r.tokensPerSec)
        }
        return scope.async {
            mutex.withLock {
                ensureLoaded(model, s, threads)
                _status.value = ModelStatus.BUSY
                try {
                    current?.bench() ?: BenchResult(0.0, 0.0)
                } finally {
                    _status.value = ModelStatus.READY
                }
            }
        }.await()
    }

    /** Info runtime dari llama.cpp untuk model GGUF yang sedang dimuat. */
    suspend fun runtimeInfo(): String? = scope.async { mutex.withLock { current?.info() } }.await()
}

/** Memisahkan blok pikiran (<thought>…</thought> / <think>…</think>) dari jawaban akhir. */
object ThoughtSplitter {
    private val tags = listOf("thought", "think", "thinking")

    fun split(raw: String): Pair<String, String> {
        var text = raw
        val thought = StringBuilder()
        for (tag in tags) {
            val open = "<$tag>"
            val close = "</$tag>"
            while (true) {
                val s = text.indexOf(open, ignoreCase = true)
                if (s < 0) break
                val e = text.indexOf(close, s + open.length, ignoreCase = true)
                if (e < 0) {
                    thought.append(text.substring(s + open.length))
                    text = text.substring(0, s)
                    break
                }
                thought.append(text.substring(s + open.length, e)).append('\n')
                text = text.removeRange(s, e + close.length)
            }
        }
        return text to thought.toString().trim()
    }
}
