package com.gemmaagent.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class Role { USER, AGENT }

enum class TaskStatus { RUNNING, DONE, STOPPED, ERROR }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val actions: List<String> = emptyList(),
    val status: TaskStatus? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

/** Ringkasan satu percakapan untuk memori konteks AI (dibatasi agar muat di 1536 token). */
data class MemoryItem(val command: String, val result: String)

/**
 * Menyimpan riwayat chat secara lokal (file JSON di memori internal)
 * dan memori konteks singkat yang dikirim ke AI.
 */
class ChatRepository(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "chat_history.json")
    private val saveMutex = Mutex()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _memory = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memory: StateFlow<List<MemoryItem>> = _memory.asStateFlow()

    init {
        load()
    }

    fun add(message: ChatMessage) {
        _messages.update { it + message }
        persist()
    }

    fun update(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { list -> list.map { if (it.id == id) transform(it) else it } }
        persist()
    }

    fun remember(command: String, result: String) {
        _memory.update { (it + MemoryItem(command.take(160), result.take(200))).takeLast(MAX_MEMORY) }
        persist()
    }

    /** Tombol "Sesi Baru / Reset Memori". */
    fun resetSession() {
        _messages.value = emptyList()
        _memory.value = emptyList()
        persist()
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("messages") ?: JSONArray()
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val acts = o.optJSONArray("actions")
                val actions = if (acts == null) emptyList() else List(acts.length()) { acts.getString(it) }
                var status = o.optString("status", "").takeIf { it.isNotEmpty() }?.let {
                    runCatching { TaskStatus.valueOf(it) }.getOrNull()
                }
                // Tugas yang masih "berjalan" saat aplikasi ditutup dianggap berhenti.
                if (status == TaskStatus.RUNNING) status = TaskStatus.STOPPED
                list += ChatMessage(
                    id = o.getString("id"),
                    role = Role.valueOf(o.getString("role")),
                    text = o.getString("text"),
                    actions = actions,
                    status = status,
                    timestamp = o.optLong("ts", 0L),
                )
            }
            _messages.value = list
            val mem = root.optJSONArray("memory") ?: JSONArray()
            _memory.value = List(mem.length()) {
                val m = mem.getJSONObject(it)
                MemoryItem(m.getString("c"), m.getString("r"))
            }
        } catch (_: Throwable) {
            // File rusak: mulai dari kosong.
        }
    }

    private fun persist() {
        val snapshotMessages = _messages.value.takeLast(MAX_STORED)
        val snapshotMemory = _memory.value
        scope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                try {
                    val arr = JSONArray()
                    snapshotMessages.forEach { m ->
                        arr.put(JSONObject().apply {
                            put("id", m.id)
                            put("role", m.role.name)
                            put("text", m.text)
                            put("actions", JSONArray(m.actions))
                            put("status", m.status?.name ?: "")
                            put("ts", m.timestamp)
                        })
                    }
                    val mem = JSONArray()
                    snapshotMemory.forEach { mem.put(JSONObject().put("c", it.command).put("r", it.result)) }
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(JSONObject().put("messages", arr).put("memory", mem).toString())
                    tmp.renameTo(file)
                } catch (_: Throwable) {
                }
            }
        }
    }

    companion object {
        private const val MAX_MEMORY = 3
        private const val MAX_STORED = 300
    }
}
