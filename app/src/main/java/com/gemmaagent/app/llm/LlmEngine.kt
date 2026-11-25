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
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()

    private var engine: Engine? = null

    private val _status = MutableStateFlow(ModelStatus.NOT_LOADED)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Memuat model di background (dipanggil oleh Foreground Service). */
    fun loadAsync() {
        scope.launch { mutex.withLock { ensureLoaded() } }
    }

    private fun ensureLoaded() {
        if (engine != null) return
        val file = GemmaAgentApp.instance.modelRepository.modelFile
        if (!file.exists()) {
            _status.value = ModelStatus.ERROR
            _error.value = "File model belum disalin. Pilih file model di Beranda."
            throw IllegalStateException(_error.value)
        }
        _status.value = ModelStatus.LOADING
        _error.value = null
        try {
            val config = EngineConfig(
                modelPath = file.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = MAX_TOKENS,
                cacheDir = context.cacheDir.absolutePath,
            )
            val e = Engine(config)
            e.initialize()
            engine = e
            _status.value = ModelStatus.READY
        } catch (t: Throwable) {
            engine = null
            _status.value = ModelStatus.ERROR
            _error.value = "Gagal memuat model: ${t.message ?: t.javaClass.simpleName}"
            throw t
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
                _status.value = ModelStatus.BUSY
                val conversation = eng.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemPrompt),
                        samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.2),
                    )
                )
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
                _status.value = ModelStatus.NOT_LOADED
            }
        }
    }

    companion object {
        const val MAX_TOKENS = 1536
        const val MODEL_LABEL = "Gemma 4 E2B"
    }
}
