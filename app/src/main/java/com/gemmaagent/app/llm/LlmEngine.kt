package com.gemmaagent.app.llm

import android.content.Context
import com.gemmaagent.app.GemmaAgentApp
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
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
import com.gemmaagent.app.util.CrashLogger
import com.gemmaagent.app.util.DeviceInfo
import com.gemmaagent.app.util.Fmt
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.util.concurrent.Executors

enum class ModelStatus(val label: String) {
    NOT_LOADED("Belum dimuat"),
    LOADING("Memuat…"),
    READY("Siap"),
    BUSY("Berpikir…"),
    ERROR("Error"),
}

/**
 * Pembungkus mesin LiteRT-LM (penerus MediaPipe LLM Inference) untuk Gemma 4 E2B IT (.litertlm).
 *
 * - Backend WAJIB CPU (lebih stabil di MediaTek Helio G96 / RAM 8 GB).
 * - maxNumTokens = 1536 (total token input + output) agar tidak force close.
 * - Semua panggilan native berjalan di 1 thread khusus + Mutex, jadi tidak pernah tumpang-tindih.
 */
class LlmEngine(private val context: Context) {

    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "gemma-llm").apply { priority = Thread.NORM_PRIORITY }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, _ -> })
    private val mutex = Mutex()

    private var engine: Engine? = null

    private val _status = MutableStateFlow(ModelStatus.NOT_LOADED)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Memuat model di background (dipanggil oleh Foreground Service). Tidak pernah melempar error. */
    fun loadAsync() {
        scope.launch {
            mutex.withLock {
                try {
                    ensureLoaded()
                } catch (_: Throwable) {
                    // Pesan error sudah disimpan di [error] dan tampil di layar.
                }
            }
        }
    }

    private var loadedPath: String? = null

    /** Memuat model jika belum. Error dikonversi menjadi pesan yang mudah dipahami. */
    private fun ensureLoaded() {
        val path = GemmaAgentApp.instance.modelRepository.currentModelPath()
        if (engine != null && loadedPath == path) return
        if (engine != null) {
            // File model berubah (mis. pindah dari salinan internal ke folder Download).
            runCatching { engine?.close() }
            engine = null
            loadedPath = null
        }
        if (path == null || !File(path).canRead()) {
            _status.value = ModelStatus.ERROR
            val msg = "File model tidak ditemukan / tidak bisa dibaca. Buka Beranda lalu cek Langkah 1."
            _error.value = msg
            throw IllegalStateException(msg)
        }
        _status.value = ModelStatus.LOADING
        _error.value = null

        val (availMb, _) = runCatching { DeviceInfo.availableRamMb(context) }.getOrDefault(0L to 0L)
        try {
            val config = EngineConfig(
                modelPath = path,
                backend = Backend.CPU(),
                maxNumTokens = MAX_TOKENS,
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
            loadedPath = path
            _status.value = ModelStatus.READY
        } catch (t: Throwable) {
            engine = null
            loadedPath = null
            _status.value = ModelStatus.ERROR
            _error.value = friendlyError(t, availMb)
            CrashLogger.record(context, "Gagal memuat model (aplikasi tidak ditutup)", t)
            throw IllegalStateException(_error.value, t)
        }
    }

    private fun friendlyError(t: Throwable, availMb: Long): String {
        val raw = (t.message ?: t.javaClass.simpleName).take(200)
        val lower = raw.lowercase()
        return when {
            t is OutOfMemoryError || "memory" in lower || "alloc" in lower || "oom" in lower ->
                "RAM tidak cukup untuk memuat model (sisa ${Fmt.gbFromMb(availMb)} GB). Tutup aplikasi lain lalu ketuk Coba Lagi. Detail: $raw"
            "permission" in lower || "denied" in lower || "eacces" in lower ->
                "Tidak punya izin membaca file model. Aktifkan \"Akses Semua File\" di Beranda. Detail: $raw"
            "not found" in lower || "no such file" in lower ->
                "File model tidak ditemukan. Detail: $raw"
            else -> "Gagal memuat model: $raw"
        }
    }

    /**
     * Mengirim satu prompt ke model. Setiap langkah agen memakai percakapan BARU
     * (prompt sudah berisi ringkasan konteks), sehingga jumlah token tidak menumpuk
     * melewati batas 1536.
     *
     * Proses berjalan di scope milik mesin: jika agen di-STOP, pemanggil langsung
     * berhenti menunggu, sementara mesin menyelesaikan/membatalkan pekerjaannya dengan aman.
     */
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        onPartial: (String) -> Unit = {},
    ): String {
        val job = scope.async {
            mutex.withLock {
                ensureLoaded()
                val eng = engine ?: throw IllegalStateException("Model belum dimuat")
                val conversation = try {
                    eng.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(systemPrompt),
                            samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.2),
                        )
                    )
                } catch (t: Throwable) {
                    throw IllegalStateException("Gagal memulai percakapan AI: ${t.message ?: t.javaClass.simpleName}", t)
                }
                _status.value = ModelStatus.BUSY
                currentConversation = conversation
                try {
                    val sb = StringBuilder()
                    conversation.sendMessageAsync(Contents.of(userPrompt)).collect { msg ->
                        val piece = msg.toString()
                        if (piece.isNotEmpty()) {
                            sb.append(piece)
                            onPartial(sb.toString())
                        }
                    }
                    sb.toString()
                } finally {
                    currentConversation = null
                    runCatching { conversation.close() }
                    _status.value = ModelStatus.READY
                }
            }
        }
        return job.await()
    }

    @Volatile
    private var currentConversation: com.google.ai.edge.litertlm.Conversation? = null

    /** Membatalkan proses berpikir yang sedang berjalan (tombol STOP). */
    fun cancelCurrent() {
        runCatching { currentConversation?.cancelProcess() }
    }

    /** Melepas model dari RAM (dipanggil sebelum file model diganti atau saat service berhenti). */
    fun unloadBlocking() {
        cancelCurrent()
        runBlocking(dispatcher) {
            mutex.withLock {
                runCatching { engine?.close() }
                engine = null
                loadedPath = null
                _status.value = ModelStatus.NOT_LOADED
            }
        }
    }

    fun unloadAsync() {
        cancelCurrent()
        scope.launch {
            mutex.withLock {
                runCatching { engine?.close() }
                engine = null
                loadedPath = null
                _status.value = ModelStatus.NOT_LOADED
            }
        }
    }

    companion object {
        const val MAX_TOKENS = 1536
        const val MODEL_LABEL = "Gemma 4 E2B"
    }
}
