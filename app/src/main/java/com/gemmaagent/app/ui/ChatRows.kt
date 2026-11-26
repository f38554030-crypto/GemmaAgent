package com.gemmaagent.app.ui

import androidx.compose.runtime.Immutable
import com.gemmaagent.app.chat.ChatController
import com.gemmaagent.app.chat.StreamState
import com.gemmaagent.app.data.db.Kinds
import com.gemmaagent.app.data.db.MessageEntity
import com.gemmaagent.app.data.db.Roles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONObject

@Immutable
data class SourceItem(val title: String, val url: String, val host: String)

@Immutable
data class VersionInfo(val active: Int, val count: Int, val prevText: String)

/**
 * Satu baris di LazyColumn chat. Jawaban AI dipecah menjadi beberapa baris
 * (header → blok-blok Markdown → footer) dengan key unik, sehingga saat token baru masuk
 * Compose hanya merender ulang blok paling bawah — blok lain dilewati karena isinya sama.
 */
@Immutable
sealed interface ChatRow {
    val key: String
    val type: Int
}

@Immutable
data class UserRow(val msg: MessageEntity) : ChatRow {
    override val key: String get() = "u${msg.id}"
    override val type: Int get() = 1
}

@Immutable
data class AiHeaderRow(
    val msgId: Long,
    val live: Boolean,
    val phase: String,
    val sources: List<SourceItem>,
    val steps: List<String>,
    val query: String?,
    val thought: String,
    val fromCache: Boolean,
) : ChatRow {
    override val key: String get() = "h$msgId"
    override val type: Int get() = 2
}

@Immutable
data class AiBlockRow(val msgId: Long, val index: Int, val block: MdBlock) : ChatRow {
    override val key: String get() = "b${msgId}_$index"
    override val type: Int get() = 3
}

@Immutable
data class AiSpecialRow(val msg: MessageEntity) : ChatRow {
    override val key: String get() = "s${msg.id}"
    override val type: Int get() = 4
}

@Immutable
data class AiLiveRow(val msgId: Long, val phase: String, val compareFast: String?, val hasText: Boolean) : ChatRow {
    override val key: String get() = "l$msgId"
    override val type: Int get() = 5
}

@Immutable
data class AiFooterRow(
    val msg: MessageEntity,
    val isLast: Boolean,
    val versions: VersionInfo?,
    val followUps: List<String>,
) : ChatRow {
    override val key: String get() = "f${msg.id}"
    override val type: Int get() = 6
}

/**
 * Membangun daftar baris chat. Dipanggil di Dispatchers.Default (parsing Markdown & JSON
 * TIDAK pernah berjalan di thread UI). Hasil parse pesan yang sudah selesai di-cache.
 */
class ChatRowsBuilder {
    private val cache = HashMap<Long, Pair<String, List<MdBlock>>>()
    private var lastLive: StreamState? = null

    fun build(messages: List<MessageEntity>, stream: StreamState?): List<ChatRow> {
        val out = ArrayList<ChatRow>(messages.size * 4)
        val lastId = messages.lastOrNull()?.id
        val alive = HashSet<Long>()
        if (stream != null) lastLive = stream

        for (m in messages) {
            if (m.role == Roles.USER) {
                out += UserRow(m)
                continue
            }
            alive += m.id
            val live = stream != null && stream.messageId == m.id
            val payload = if (!live && m.payload.isNotBlank()) runCatching { JSONObject(m.payload) }.getOrNull() else null

            // ---------- Header: avatar, karusel sumber, langkah pencarian, pikiran ----------
            val search = payload?.optJSONObject("search")
            out += if (live) {
                AiHeaderRow(m.id, true, stream!!.phase, stream.sources.map { SourceItem(it.first, it.second, host(it.second)) },
                    stream.steps, null, stream.thought, false)
            } else {
                AiHeaderRow(
                    m.id, false, "", parseSources(payload),
                    search?.optJSONArray("steps")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList(),
                    search?.optString("query")?.takeIf { it.isNotBlank() },
                    if (m.kind != Kinds.COMPARE) m.thought else "",
                    search?.optBoolean("cache") == true,
                )
            }

            val textKind = m.kind == Kinds.TEXT || m.kind == Kinds.CALC
            when {
                live -> {
                    if (textKind || m.kind == Kinds.COMPARE) {
                        MarkdownParser.parse(stream!!.text).forEachIndexed { i, b -> out += AiBlockRow(m.id, i, b) }
                    }
                    out += AiLiveRow(m.id, stream!!.phase, stream.compareFast, stream.text.isNotBlank())
                }
                textKind -> {
                    // Celah singkat antara stream selesai dan database terbarui: pakai teks stream terakhir (anti-kedip).
                    val text = if (m.text.isEmpty() && lastLive?.messageId == m.id) lastLive!!.text else m.text
                    blocksFor(m.id, text).forEachIndexed { i, b -> out += AiBlockRow(m.id, i, b) }
                    out += footer(m, lastId, payload)
                }
                else -> {
                    out += AiSpecialRow(m)
                    out += footer(m, lastId, payload)
                }
            }
        }
        cache.keys.retainAll(alive)
        return out
    }

    private fun blocksFor(id: Long, text: String): List<MdBlock> {
        cache[id]?.let { (t, blocks) -> if (t == text) return blocks }
        val blocks = MarkdownParser.parse(text)
        cache[id] = text to blocks
        return blocks
    }

    private fun footer(m: MessageEntity, lastId: Long?, payload: JSONObject?): AiFooterRow {
        val versions = payload?.optJSONArray("versions")?.let { arr ->
            if (arr.length() < 2) null else {
                val active = payload.optInt("active", arr.length() - 1).coerceIn(0, arr.length() - 1)
                val prev = arr.optJSONObject(if (active > 0) active - 1 else 1)?.optString("text").orEmpty()
                VersionInfo(active, arr.length(), prev)
            }
        }
        val follow = payload?.optJSONArray("followups")?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
        return AiFooterRow(m, m.id == lastId, versions, follow)
    }

    private fun parseSources(payload: JSONObject?): List<SourceItem> {
        val arr = payload?.optJSONArray("citations") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.optJSONObject(i)
            val url = o?.optString("url").orEmpty()
            SourceItem(o?.optString("title").orEmpty().ifBlank { url }, url, host(url))
        }.filter { it.url.isNotBlank() }
    }

    companion object {
        fun host(url: String): String = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
    }
}

/** Aliran baris chat: digabung dari pesan + stream, dibangun di thread latar, frame lama dibuang (conflate). */
fun chatRowsFlow(chat: ChatController): Flow<List<ChatRow>> {
    val builder = ChatRowsBuilder()
    return combine(chat.messages, chat.stream) { m, s -> m to s }
        .conflate()
        .map { (m, s) -> builder.build(m, s) }
        .flowOn(Dispatchers.Default)
}
