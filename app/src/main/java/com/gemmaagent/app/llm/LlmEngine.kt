package com.gemmaagent.app.llm

import android.content.Context
import com.gemmaagent.app.util.CrashLogger
import com.gemmaagent.app.util.DeviceInfo
import com.gemmaagent.app.util.Fmt
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
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
    LOADING("Memuat…"),
    READY("Siap"),
    BUSY("Menjawab…"),
    ERROR("Error"),
}

/** Parameter yang menentukan konfigurasi mesin (perubahan = muat ulang mesin). */
data class EngineParams(
    val modelPath: String,
    val threads: Int,
    val contextTokens: Int,
    val vision: Boolean,
)

data class ChatTurn(val user: Boolean, val text: String)

data class GenRequest(
    val systemPrompt: String,
    val history: List<ChatTurn>,
    val userText: String,
    val imageBytes: ByteArray? = null,
    val temperature: Float,
    val maxOutputTokens: Int,
    val thinking: Boolean,
)

data class GenResult(
    val text: String,
    val thought: String,
    val tokens: Int,
    val tokensPerSec: Double,
    val durationMs: Long,
    val stopped: Boolean,
)

/**
 * Pembungkus LiteRT-LM untuk Gemma 4 E2B IT (.litertlm).
 * - Backend CPU dengan 4–6 thread (stabil di MediaTek Helio G96).
 * - Batas konteks 512 / 1536 / 3072 token.
 * - Setiap permintaan memakai percakapan baru berisi riwayat terpangkas (sliding window),
 *   sehingga KV cache selalu dibuang setelah menjawab → RAM tetap terkendali.
 * - Semua panggilan native di 1 thread khusus + Mutex. Semua error ditangkap → pesan di UI.
 */
class LlmEngine(private val context: Context) {

    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "gemma-llm") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, _ -> })
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var loadedParams: EngineParams? = null

    @Volatile
    private var currentConversation: Conversation? = null

    @Volatile
    private var stopRequested = false

    private val _status = MutableStateFlow(ModelStatus.NOT_LOADED)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Dipanggil saat model belum dipilih. */
    fun markNoModel() {
        if (engine == null) _status.value = ModelStatus.NO_MODEL
    }

    /** Memuat model di background. Tidak pernah melempar error (pesan tampil di UI). */
    fun loadAsync(params: EngineParams) {
        scope.launch {
            mutex.withLock {
                runCatching { ensureLoaded(params) }
            }
        }
    }

    private fun ensureLoaded(params: EngineParams) {
        if (engine != null && loadedParams == params) return
        // Jika vision sudah aktif, permintaan tanpa vision tidak perlu memuat ulang.
        val lp = loadedParams
        if (engine != null && lp != null && lp.copy(vision = true) == params.copy(vision = true) && lp.vision && !params.vision) return

        closeEngine()
        if (!File(params.modelPath).canRead()) {
            val msg = "File model tidak bisa dibaca. Buka Pengaturan → Model, lalu pindai ulang / izinkan Akses Semua File."
            _status.value = ModelStatus.ERROR
            _error.value = msg
            throw IllegalStateException(msg)
        }
        _status.value = ModelStatus.LOADING
        _error.value = null
        val (availMb, _) = runCatching { DeviceInfo.availableRamMb(context) }.getOrDefault(0L to 0L)
        try {
            val cpu = Backend.CPU(threadCount = params.threads)
            val config = EngineConfig(
                modelPath = params.modelPath,
                backend = cpu,
                visionBackend = if (params.vision) Backend.CPU(threadCount = params.threads) else null,
                maxNumTokens = params.contextTokens,
                maxNumImages = if (params.vision) 1 else null,
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
            loadedParams = params
            _status.value = ModelStatus.READY
        } catch (t: Throwable) {
            engine = null
            loadedParams = null
            _status.value = ModelStatus.ERROR
            _error.value = friendlyError(t, availMb, params.vision)
            CrashLogger.record(context, "Gagal memuat model (aplikasi tidak ditutup)", t)
            throw IllegalStateException(_error.value, t)
        }
    }

    private fun closeEngine() {
        runCatching { engine?.close() }
        engine = null
        loadedParams = null
    }

    private fun friendlyError(t: Throwable, availMb: Long, vision: Boolean): String {
        val raw = (t.message ?: t.javaClass.simpleName).take(220)
        val lower = raw.lowercase()
        return when {
            t is OutOfMemoryError || "memory" in lower || "alloc" in lower || "oom" in lower ->
                "RAM tidak cukup untuk memuat model (sisa ${Fmt.gbFromMb(availMb)} GB). Tutup aplikasi lain, kecilkan batas konteks di Pengaturan, lalu Coba Lagi. Detail: $raw"
            vision -> "Model gagal dimuat dengan mode gambar (vision). Model ini mungkin tidak mendukung input gambar. Detail: $raw"
            "permission" in lower || "denied" in lower || "eacces" in lower ->
                "Tidak punya izin membaca file model. Aktifkan \"Akses Semua File\" di Pengaturan. Detail: $raw"
            else -> "Gagal memuat model: $raw"
        }
    }

    /**
     * Menghasilkan jawaban dengan streaming. [onUpdate] dipanggil dengan teks & pikiran sejauh ini.
     * Proses berjalan di scope mesin; jika pengguna menekan Stop, native dibatalkan lewat cancelProcess().
     */
    suspend fun generate(
        params: EngineParams,
        req: GenRequest,
        onUpdate: (text: String, thought: String) -> Unit,
    ): GenResult {
        val job = scope.async {
            mutex.withLock {
                stopRequested = false
                ensureLoaded(params)
                val eng = engine ?: throw IllegalStateException("Model belum dimuat")

                val initial = req.history.map { if (it.user) Message.user(it.text) else Message.model(it.text) }
                val sampler = SamplerConfig(
                    topK = 40,
                    topP = 0.95,
                    temperature = req.temperature.toDouble().coerceIn(0.05, 1.5),
                )
                fun config(withThinking: Boolean) = ConversationConfig(
                    systemInstruction = Contents.of(req.systemPrompt),
                    initialMessages = initial,
                    samplerConfig = sampler,
                    maxOutputToken = req.maxOutputTokens,
                    thinkingConfig = if (withThinking) ThinkingConfig(enableThinking = req.thinking) else null,
                )
                val conversation = try {
                    eng.createConversation(config(true))
                } catch (_: Throwable) {
                    // Model tanpa dukungan konfigurasi thinking → coba tanpa.
                    try {
                        eng.createConversation(config(false))
                    } catch (t: Throwable) {
                        throw IllegalStateException(conversationError(t), t)
                    }
                }
                currentConversation = conversation
                _status.value = ModelStatus.BUSY

                val contents = if (req.imageBytes != null) {
                    Contents.of(Content.ImageBytes(req.imageBytes), Content.Text(req.userText))
                } else {
                    Contents.of(req.userText)
                }

                val raw = StringBuilder()
                val channelThought = StringBuilder()
                var tokens = 0
                val start = System.currentTimeMillis()
                var firstTokenAt = 0L
                try {
                    conversation.sendMessageAsync(contents).collect { msg ->
                        val piece = msg.toString()
                        val ch = msg.channels.values.joinToString("")
                        if (piece.isNotEmpty() || ch.isNotEmpty()) {
                            if (firstTokenAt == 0L) firstTokenAt = System.currentTimeMillis()
                            tokens++
                            raw.append(piece)
                            channelThought.append(ch)
                            val (t, th) = ThoughtSplitter.split(raw.toString())
                            onUpdate(t, (channelThought.toString() + th).trim())
                        }
                    }
                } catch (t: Throwable) {
                    if (!stopRequested) {
                        val msg = (t.message ?: "").lowercase()
                        if ("max" in msg && "token" in msg) {
                            // Konteks penuh: kembalikan yang sudah ada.
                        } else {
                            throw IllegalStateException("Gagal menghasilkan jawaban: ${t.message ?: t.javaClass.simpleName}", t)
                        }
                    }
                } finally {
                    currentConversation = null
                    runCatching { conversation.close() }
                    _status.value = ModelStatus.READY
                }
                val end = System.currentTimeMillis()
                val decodeMs = if (firstTokenAt > 0) (end - firstTokenAt).coerceAtLeast(1) else 1
                val (text, th) = ThoughtSplitter.split(raw.toString())
                GenResult(
                    text = text.trim(),
                    thought = (channelThought.toString() + th).trim(),
                    tokens = tokens,
                    tokensPerSec = if (tokens > 1) (tokens - 1) * 1000.0 / decodeMs else 0.0,
                    durationMs = end - start,
                    stopped = stopRequested,
                )
            }
        }
        return job.await()
    }

    private fun conversationError(t: Throwable): String {
        val m = (t.message ?: t.javaClass.simpleName).take(200)
        return if ("token" in m.lowercase() || "context" in m.lowercase()) {
            "Teks terlalu panjang untuk batas konteks. Mulai sesi baru atau naikkan batas konteks. Detail: $m"
        } else "Gagal memulai percakapan AI: $m"
    }

    /** Tombol Stop Generasi Instan. */
    fun stop() {
        stopRequested = true
        runCatching { currentConversation?.cancelProcess() }
    }

    /** Pembersih cache & memori: lepas mesin, hapus file cache, lalu muat ulang saat dibutuhkan. */
    fun clearCaches(onDone: () -> Unit = {}) {
        stop()
        scope.launch {
            mutex.withLock {
                closeEngine()
                runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
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
                closeEngine()
                _status.value = ModelStatus.NOT_LOADED
            }
        }
    }
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
