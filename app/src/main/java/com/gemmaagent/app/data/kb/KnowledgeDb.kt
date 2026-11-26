package com.gemmaagent.app.data.kb

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Arsip hasil pencarian web untuk dibaca offline (fitur 15). */
@Entity(tableName = "web_cache")
data class WebCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val url: String,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Indeks teks lengkap arsip web. */
@Fts4(contentEntity = WebCacheEntity::class)
@Entity(tableName = "web_cache_fts")
data class WebCacheFts(val query: String, val title: String, val content: String)

/** Dokumen di Basis Pengetahuan Lokal (fitur 19). */
@Entity(tableName = "kb_docs")
data class KbDocEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val chars: Int,
    val chunks: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Potongan dokumen yang diindeks FTS (Android menjamin FTS4; FTS5 tidak tersedia di semua HP). */
@Fts4
@Entity(tableName = "kb_chunks")
data class KbChunkFts(val docRef: String, val docName: String, val text: String)

data class KbHit(val docName: String, val text: String)

@Dao
interface KnowledgeDao {
    // ---- arsip web ----
    @Insert suspend fun insertWeb(e: WebCacheEntity): Long

    @Query("SELECT * FROM web_cache ORDER BY createdAt DESC")
    fun observeWeb(): Flow<List<WebCacheEntity>>

    @Query("SELECT * FROM web_cache ORDER BY createdAt DESC")
    suspend fun allWeb(): List<WebCacheEntity>

    @Query("SELECT COUNT(*) FROM web_cache WHERE url = :url")
    suspend fun countUrl(url: String): Int

    @Query("SELECT web_cache.* FROM web_cache JOIN web_cache_fts ON web_cache.rowid = web_cache_fts.rowid WHERE web_cache_fts MATCH :match LIMIT :limit")
    suspend fun searchWeb(match: String, limit: Int): List<WebCacheEntity>

    @Query("DELETE FROM web_cache WHERE id = :id") suspend fun deleteWeb(id: Long)
    @Query("DELETE FROM web_cache") suspend fun clearWeb()

    // ---- basis pengetahuan ----
    @Insert suspend fun insertDoc(d: KbDocEntity): Long
    @Insert suspend fun insertChunks(c: List<KbChunkFts>)

    @Query("SELECT * FROM kb_docs ORDER BY createdAt DESC")
    fun observeDocs(): Flow<List<KbDocEntity>>

    @Query("SELECT COUNT(*) FROM kb_docs") suspend fun docCount(): Int

    @Query("SELECT docName, text FROM kb_chunks WHERE kb_chunks MATCH :match LIMIT :limit")
    suspend fun searchChunks(match: String, limit: Int): List<KbHit>

    @Query("DELETE FROM kb_docs WHERE id = :id") suspend fun deleteDoc(id: Long)
    @Query("DELETE FROM kb_chunks WHERE docRef = :docRef") suspend fun deleteChunks(docRef: String)
}

@Database(
    entities = [WebCacheEntity::class, WebCacheFts::class, KbDocEntity::class, KbChunkFts::class],
    version = 1,
    exportSchema = false,
)
abstract class KnowledgeDb : RoomDatabase() {
    abstract fun dao(): KnowledgeDao

    companion object {
        fun create(context: Context): KnowledgeDb =
            Room.databaseBuilder(context, KnowledgeDb::class.java, "knowledge.db")
                .fallbackToDestructiveMigration(true)
                .build()

        /** Ubah teks bebas menjadi kueri FTS aman: kata1* OR kata2* … */
        fun ftsQuery(text: String, maxTerms: Int = 8): String? {
            val stop = setOf("yang", "dan", "di", "ke", "dari", "ini", "itu", "apa", "adalah", "untuk", "dengan", "atau",
                "the", "a", "an", "of", "to", "is", "in", "and", "saya", "kamu", "tolong", "jelaskan", "bagaimana", "berapa")
            val terms = Regex("[\\p{L}\\p{N}]{3,}").findAll(text.lowercase()).map { it.value }
                .filter { it !in stop }.distinct().take(maxTerms).toList()
            if (terms.isEmpty()) return null
            return terms.joinToString(" OR ") { "$it*" }
        }
    }
}
