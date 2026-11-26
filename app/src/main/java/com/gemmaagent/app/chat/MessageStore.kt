package com.gemmaagent.app.chat

import com.gemmaagent.app.data.db.MessageDao
import com.gemmaagent.app.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** Penyimpanan pesan: database (biasa) atau RAM saja (Sesi Incognito, tidak pernah ditulis ke disk). */
interface MessageStore {
    fun observe(sessionId: Long): Flow<List<MessageEntity>>
    suspend fun list(sessionId: Long): List<MessageEntity>
    suspend fun get(id: Long): MessageEntity?
    suspend fun insert(m: MessageEntity): Long
    suspend fun update(m: MessageEntity)
    suspend fun setPayload(id: Long, payload: String)
    suspend fun setStarred(id: Long, starred: Boolean)
    suspend fun delete(id: Long)
    suspend fun deleteFrom(sessionId: Long, fromId: Long)
}

class RoomMessageStore(private val dao: MessageDao) : MessageStore {
    override fun observe(sessionId: Long) = dao.observe(sessionId)
    override suspend fun list(sessionId: Long) = dao.list(sessionId)
    override suspend fun get(id: Long) = dao.get(id)
    override suspend fun insert(m: MessageEntity) = dao.insert(m)
    override suspend fun update(m: MessageEntity) = dao.update(m)
    override suspend fun setPayload(id: Long, payload: String) = dao.setPayload(id, payload)
    override suspend fun setStarred(id: Long, starred: Boolean) = dao.setStarred(id, starred)
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun deleteFrom(sessionId: Long, fromId: Long) = dao.deleteFrom(sessionId, fromId)
}

class MemoryMessageStore : MessageStore {
    private val items = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val ids = AtomicLong(1)

    fun clear() {
        items.value = emptyList()
    }

    override fun observe(sessionId: Long): Flow<List<MessageEntity>> = items.map { l -> l.filter { it.sessionId == sessionId } }
    override suspend fun list(sessionId: Long) = items.value.filter { it.sessionId == sessionId }
    override suspend fun get(id: Long) = items.value.firstOrNull { it.id == id }
    override suspend fun insert(m: MessageEntity): Long {
        val id = ids.getAndIncrement()
        items.update { it + m.copy(id = id) }
        return id
    }
    override suspend fun update(m: MessageEntity) = items.update { l -> l.map { if (it.id == m.id) m else it } }
    override suspend fun setPayload(id: Long, payload: String) = items.update { l -> l.map { if (it.id == id) it.copy(payload = payload) else it } }
    override suspend fun setStarred(id: Long, starred: Boolean) = items.update { l -> l.map { if (it.id == id) it.copy(starred = starred) else it } }
    override suspend fun delete(id: Long) = items.update { l -> l.filter { it.id != id } }
    override suspend fun deleteFrom(sessionId: Long, fromId: Long) = items.update { l -> l.filterNot { it.sessionId == sessionId && it.id >= fromId } }
}
