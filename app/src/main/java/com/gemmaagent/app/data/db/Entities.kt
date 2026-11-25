package com.gemmaagent.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

object Roles {
    const val USER = "user"
    const val ASSISTANT = "assistant"
}

/** Jenis isi pesan untuk tampilan khusus. */
object Kinds {
    const val TEXT = "text"
    const val CALC = "calc"
    const val QUIZ = "quiz"
    const val FLASHCARDS = "flashcards"
    const val CHECKLIST = "checklist"
    const val COMPARE = "compare"
    const val ERROR = "error"
}

@Entity(
    tableName = "messages",
    indices = [Index("sessionId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val text: String,
    val thought: String = "",
    val kind: String = Kinds.TEXT,
    /** Data tambahan dalam JSON: sitasi, saran lanjutan, kuis, checklist, perbandingan. */
    val payload: String = "",
    val imagePath: String? = null,
    val attachmentName: String? = null,
    /** Teks dokumen/URL yang dilampirkan (disimpan agar bisa regenerasi). */
    val attachmentText: String? = null,
    val starred: Boolean = false,
    val online: Boolean = false,
    val tokensPerSec: Double = 0.0,
    val tokenCount: Int = 0,
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fact: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    val sourceMessageId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
