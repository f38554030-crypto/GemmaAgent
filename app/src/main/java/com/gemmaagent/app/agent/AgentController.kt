package com.gemmaagent.app.agent

import android.content.Context
import com.gemmaagent.app.data.ChatMessage
import com.gemmaagent.app.data.ChatRepository
import com.gemmaagent.app.data.Role
import com.gemmaagent.app.data.TaskStatus
import com.gemmaagent.app.llm.LlmEngine
import com.gemmaagent.app.service.AgentAccessibilityService
import com.gemmaagent.app.service.AgentForegroundService
import com.gemmaagent.app.util.DeviceInfo
import com.gemmaagent.app.util.FeedbackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class LogKind { INFO, THINK, ACTION, WARN, DONE, ERROR }

data class LogEntry(val text: String, val kind: LogKind, val time: Long = System.currentTimeMillis())

/** Permintaan persetujuan untuk aksi sensitif (Safety Guard). */
data class ConfirmRequest(
    val actionLabel: String,
    val riskyWord: String,
    val deferred: CompletableDeferred<Boolean>,
)

/**
 * Otak agen: loop "baca layar → tanya Gemma → jalankan aksi JSON".
 * - Maksimal 15 langkah per perintah.
 * - Jeda 1,5 detik antar-aksi.
 * - Deteksi aksi berulang (anti-looping).
 * - Aksi sensitif wajib disetujui pengguna.
 */
class AgentController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val llm: LlmEngine,
    private val chat: ChatRepository,
    private val feedback: FeedbackManager,
) {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _statusLine = MutableStateFlow("Siap menerima perintah")
    val statusLine: StateFlow<String> = _statusLine.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _liveThought = MutableStateFlow("")
    val liveThought: StateFlow<String> = _liveThought.asStateFlow()

    private val _confirm = MutableStateFlow<ConfirmRequest?>(null)
    val confirm: StateFlow<ConfirmRequest?> = _confirm.asStateFlow()

    private var job: Job? = null
    private var currentAgentMsgId: String? = null
    private var currentCommand: String = ""
    private val executedActions = mutableListOf<String>()

    fun runCommand(command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty() || _running.value) return
        _running.value = true
        currentCommand = cmd
        executedActions.clear()
        _log.value = emptyList()
        _liveThought.value = ""

        chat.add(ChatMessage(role = Role.USER, text = cmd))
        val agentMsg = ChatMessage(role = Role.AGENT, text = "Sedang mengerjakan…", status = TaskStatus.RUNNING)
        currentAgentMsgId = agentMsg.id
        chat.add(agentMsg)

        AgentForegroundService.start(context)

        job = scope.launch(Dispatchers.Default) {
            try {
                loop(cmd)
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    finish("Agen dihentikan oleh pengguna.", TaskStatus.STOPPED, speak = false)
                }
            } catch (t: Throwable) {
                finish("Terjadi kesalahan: ${t.message ?: t.javaClass.simpleName}", TaskStatus.ERROR, speak = false)
            }
        }
    }

    /** Tombol STOP (di layar chat, jendela melayang, atau notifikasi). */
    fun stop() {
        if (!_running.value) return
        feedback.stopHaptic()
        _confirm.value?.deferred?.complete(false)
        llm.cancelCurrent()
        job?.cancel()
    }

    fun answerConfirmation(allow: Boolean) {
        _confirm.value?.deferred?.complete(allow)
    }

    private suspend fun loop(command: String) {
        val history = mutableListOf<String>()
        val recentKeys = ArrayDeque<String>()
        var lastSignature = 0
        var lastKey = ""
        var invalidCount = 0

        for (step in 1..MAX_STEPS) {
            kotlin.coroutines.coroutineContext.ensureActive()

            val svc = AgentAccessibilityService.instance
            if (svc == null) {
                finish("Layanan Aksesibilitas belum aktif. Aktifkan dulu di Beranda.", TaskStatus.ERROR, speak = false)
                return
            }

            setStatus("Membaca layar…", LogKind.INFO)
            val screen = svc.readScreen()
            val ownApp = screen.packageName == context.packageName
            val signature = screen.signature()
            if (step > 1 && signature == lastSignature && lastKey.isNotEmpty()) {
                history += "   (catatan: layar TIDAK berubah setelah aksi terakhir, coba cara lain)"
            }
            lastSignature = signature

            val prompt = buildPrompt(command, history, screen, ownApp)
            setStatus("Berpikir… (langkah $step/$MAX_STEPS)", LogKind.INFO)
            _liveThought.value = ""
            val raw = llm.generate(SYSTEM_PROMPT, prompt) { partial -> _liveThought.value = partial }
            kotlin.coroutines.coroutineContext.ensureActive()

            val parsed = ActionParser.parse(raw)
            if (parsed == null) {
                // Model menjawab teks biasa (bukan JSON) → anggap sebagai jawaban akhir.
                if (!raw.contains('{') && raw.isNotBlank()) {
                    finish(raw.trim(), TaskStatus.DONE)
                    return
                }
                invalidCount++
                addLog("Jawaban AI tidak valid, mencoba lagi…", LogKind.WARN)
                history += "$step. (jawabanmu bukan JSON valid — balas HANYA satu objek JSON)"
                if (invalidCount >= 3) {
                    finish("AI gagal memberi perintah yang valid. Coba ulangi dengan kalimat lebih sederhana.", TaskStatus.ERROR, speak = false)
                    return
                }
                continue
            }

            if (parsed.thought.isNotBlank()) addLog("💭 ${parsed.thought.take(160)}", LogKind.THINK)
            val action = parsed.action

            if (action is AgentAction.Done) {
                finish(action.summary, TaskStatus.DONE)
                return
            }

            // ---- Anti-looping ----
            val key = action.key()
            recentKeys.addLast(key)
            while (recentKeys.size > 3) recentKeys.removeFirst()
            if (recentKeys.size == 3 && recentKeys.all { it == key }) {
                finish(
                    "Agen dihentikan: terdeteksi mengulang aksi yang sama 3x (${action.describe()}). Coba beri perintah yang lebih spesifik.",
                    TaskStatus.STOPPED, speak = false,
                )
                return
            }
            lastKey = key

            // ---- Jangan ketuk/ketik di dalam aplikasi ini sendiri ----
            if (ownApp && (action is AgentAction.ClickText || action is AgentAction.TypeText || action is AgentAction.Swipe)) {
                history += "$step. ${action.describe()} → DITOLAK (layar masih Gemma Agent; buka aplikasi tujuan dulu dengan open_app atau home)"
                addLog("Masih di layar Gemma Agent, meminta AI membuka aplikasi tujuan…", LogKind.WARN)
                continue
            }

            // ---- Safety Guard ----
            if (action is AgentAction.ClickText) {
                val matched = svc.findNodeText(action.text)
                val risky = SafetyGuard.riskyWord(action.text, matched)
                if (risky != null) {
                    setStatus("⚠ Menunggu persetujuan: \"${action.text}\"", LogKind.WARN)
                    feedback.warnHaptic()
                    val allowed = askPermission(action.describe(), risky)
                    if (!allowed) {
                        recordAction("${action.describe()} — DITOLAK pengguna")
                        finish("Aksi \"${action.text}\" tidak dijalankan karena tidak diizinkan.", TaskStatus.STOPPED, speak = false)
                        return
                    }
                    addLog("Pengguna mengizinkan \"${action.text}\"", LogKind.INFO)
                }
            }

            // ---- Eksekusi ----
            setStatus("${action.describe()}…", LogKind.ACTION)
            val (ok, note) = execute(svc, action)
            recordAction("${action.describe()} ${if (ok) "✓" else "✗"}")
            history += "$step. ${action.describe()} → ${if (ok) "berhasil" else "GAGAL"}${note?.let { " ($it)" } ?: ""}"
            if (!ok) addLog("Gagal: ${action.describe()}${note?.let { " ($it)" } ?: ""}", LogKind.WARN)

            // Jeda agar layar sempat memuat halaman baru.
            delay(STEP_DELAY_MS)
        }
        finish(
            "Batas maksimal $MAX_STEPS langkah tercapai. Agen berhenti agar tidak berputar-putar. Langkah yang sudah dilakukan tercatat di kartu aksi.",
            TaskStatus.STOPPED,
        )
    }

    private suspend fun askPermission(label: String, risky: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _confirm.value = ConfirmRequest(label, risky, deferred)
        return try {
            withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { deferred.await() } ?: false
        } finally {
            _confirm.value = null
        }
    }

    private suspend fun execute(svc: AgentAccessibilityService, action: AgentAction): Pair<Boolean, String?> =
        withContext(Dispatchers.Main) {
            when (action) {
                is AgentAction.ClickText -> {
                    val ok = svc.clickText(action.text)
                    ok to if (ok) null else "teks tidak ditemukan di layar"
                }
                is AgentAction.TypeText -> {
                    val ok = svc.typeText(action.text)
                    ok to if (ok) null else "tidak ada kolom ketik"
                }
                is AgentAction.Swipe -> svc.swipe(action.direction) to null
                is AgentAction.OpenApp -> {
                    val label = runCatching { svc.openApp(action.name) }.getOrNull()
                    (label != null) to (label?.let { "terbuka: $it" } ?: "aplikasi tidak ditemukan")
                }
                AgentAction.Back -> svc.back() to null
                AgentAction.Home -> svc.home() to null
                AgentAction.OpenNotifications -> svc.openNotifications() to null
                is AgentAction.Done -> true to null
            }
        }

    private fun buildPrompt(
        command: String,
        history: List<String>,
        screen: AgentAccessibilityService.ScreenSnapshot,
        ownApp: Boolean,
    ): String {
        val sb = StringBuilder()
        sb.append("PERINTAH PENGGUNA: ").append(command.take(300)).append('\n')
        val mem = chat.memory.value
        if (mem.isNotEmpty()) {
            sb.append("KONTEKS SEBELUMNYA:\n")
            mem.forEach { sb.append("- \"").append(it.command.take(80)).append("\" → ").append(it.result.take(100)).append('\n') }
        }
        sb.append("INFO HP: ").append(runCatching { DeviceInfo.summary(context) }.getOrDefault("-")).append('\n')
        if (history.isNotEmpty()) {
            sb.append("LANGKAH YANG SUDAH DILAKUKAN:\n")
            history.takeLast(6).forEach { sb.append(it.take(160)).append('\n') }
        }
        if (ownApp) {
            sb.append("LAYAR SEKARANG: aplikasi Gemma Agent (aplikasi ini sendiri). Jangan klik di sini; gunakan open_app / home / open_notifications, atau done jika tidak perlu aksi layar.\n")
        } else {
            sb.append("LAYAR SEKARANG (aplikasi ").append(screen.packageName).append("):\n")
            sb.append(screen.toPrompt())
        }
        sb.append("Balas dengan SATU objek JSON untuk aksi berikutnya:")
        return sb.toString()
    }

    private fun recordAction(text: String) {
        executedActions += text
        val id = currentAgentMsgId ?: return
        val snapshot = executedActions.toList()
        chat.update(id) { it.copy(actions = snapshot) }
    }

    private fun setStatus(text: String, kind: LogKind) {
        _statusLine.value = text
        addLog(text, kind)
    }

    private fun addLog(text: String, kind: LogKind) {
        _log.update { (it + LogEntry(text, kind)).takeLast(60) }
    }

    private fun finish(summary: String, status: TaskStatus, speak: Boolean = true) {
        if (!_running.value) return
        val kind = when (status) {
            TaskStatus.DONE -> LogKind.DONE
            TaskStatus.ERROR -> LogKind.ERROR
            else -> LogKind.WARN
        }
        val shortStatus = when (status) {
            TaskStatus.DONE -> "Selesai ✓"
            TaskStatus.STOPPED -> "Dihentikan"
            TaskStatus.ERROR -> "Error"
            TaskStatus.RUNNING -> "Berjalan"
        }
        addLog(if (status == TaskStatus.DONE) "Selesai: ${summary.take(120)}" else summary.take(160), kind)
        _statusLine.value = shortStatus
        _liveThought.value = ""
        currentAgentMsgId?.let { id -> chat.update(id) { it.copy(text = summary, status = status) } }
        chat.remember(currentCommand, summary)
        _running.value = false
        _confirm.value?.deferred?.complete(false)
        if (status == TaskStatus.DONE) feedback.taskDone(summary, speak) else feedback.taskEnded()
    }

    companion object {
        const val MAX_STEPS = 15
        const val STEP_DELAY_MS = 1500L
        const val CONFIRM_TIMEOUT_MS = 60_000L

        val SYSTEM_PROMPT = """
Kamu adalah Gemma Agent, asisten AI offline yang mengendalikan HP Android pengguna lewat aksi layar.
Balas HANYA dengan SATU objek JSON tanpa teks lain, format:
{"thought":"alasan singkat","action":"NAMA_AKSI", ...}
Aksi yang tersedia:
{"action":"open_app","app":"WhatsApp"}
{"action":"click_text","text":"teks persis yang ada di layar"}
{"action":"type_text","text":"teks yang diketik"}
{"action":"swipe","direction":"up|down|left|right"}
{"action":"back"}
{"action":"home"}
{"action":"open_notifications"}
{"action":"done","summary":"hasil atau jawaban singkat untuk pengguna"}
Aturan:
- Satu aksi per balasan. Perhatikan daftar layar dan langkah yang sudah dilakukan.
- click_text hanya boleh memakai teks yang benar-benar ada di layar.
- Jika tugas selesai, atau pengguna hanya bertanya (misal info baterai/Wi-Fi dari INFO HP), langsung pakai "done" berisi jawabannya.
- Jangan ulangi aksi yang gagal; coba cara lain.
- Gunakan Bahasa Indonesia.
""".trim()
    }
}
