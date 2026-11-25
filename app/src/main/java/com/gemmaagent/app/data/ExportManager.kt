package com.gemmaagent.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.gemmaagent.app.data.db.AppDatabase
import com.gemmaagent.app.data.db.MemoryEntity
import com.gemmaagent.app.data.db.MessageEntity
import com.gemmaagent.app.data.db.NoteEntity
import com.gemmaagent.app.data.db.Roles
import com.gemmaagent.app.data.db.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ekspor percakapan (fitur 27), kartu kutipan (fitur 28), backup & restore (fitur 29). */
class ExportManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val dateFmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale("id", "ID"))

    // ------------------------- Ekspor MD / TXT -------------------------

    suspend fun exportSession(sessionId: Long, markdown: Boolean): String = withContext(Dispatchers.IO) {
        val session = db.sessions().get(sessionId)
        val msgs = db.messages().list(sessionId)
        val sb = StringBuilder()
        val title = session?.title ?: "Percakapan"
        if (markdown) sb.append("# ").append(title).append("\n\n") else sb.append(title).append("\n").append("=".repeat(title.length)).append("\n\n")
        sb.append(if (markdown) "_Diekspor dari GemmaChat Hybrid • ${dateFmt.format(Date())}_\n\n" else "Diekspor dari GemmaChat Hybrid • ${dateFmt.format(Date())}\n\n")
        for (m in msgs) {
            val who = if (m.role == Roles.USER) "Anda" else "Gemma"
            if (markdown) {
                sb.append("### ").append(who).append(" — ").append(dateFmt.format(Date(m.createdAt))).append("\n\n")
                m.attachmentName?.let { sb.append("> 📎 ").append(it).append("\n\n") }
                sb.append(m.text).append("\n\n")
            } else {
                sb.append("[").append(who).append(" • ").append(dateFmt.format(Date(m.createdAt))).append("]\n")
                m.attachmentName?.let { sb.append("(Lampiran: ").append(it).append(")\n") }
                sb.append(m.text.replace(Regex("[*#`]"), "")).append("\n\n")
            }
        }
        sb.toString()
    }

    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("Tidak bisa menulis file")
    }

    // ------------------------- Kartu Kutipan -------------------------

    /** Merender jawaban AI menjadi gambar kartu AMOLED lalu membuka lembar Bagikan. */
    suspend fun shareQuoteCard(text: String, accentArgb: Int): Intent = withContext(Dispatchers.Default) {
        val clean = text.replace(Regex("```[\\s\\S]*?```"), "[kode]").replace(Regex("[*#`>_]"), "").trim().take(900)
        val width = 1080
        val pad = 90f
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(236, 236, 241)
            textSize = if (clean.length < 200) 54f else if (clean.length < 450) 44f else 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(clean, 0, clean.length, bodyPaint, (width - pad * 2).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1.1f)
            .build()
        val height = (layout.height + pad * 2 + 260).toInt().coerceAtLeast(900)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.BLACK)
        val card = RectF(36f, 36f, width - 36f, height - 36f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 13, 15) }
        c.drawRoundRect(card, 48f, 48f, cardPaint)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), accentArgb, Color.rgb(110, 86, 207), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(card, 48f, 48f, border)
        val quote = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentArgb; textSize = 160f; typeface = Typeface.DEFAULT_BOLD }
        c.drawText("“", pad, pad + 120f, quote)
        c.save()
        c.translate(pad, pad + 150f)
        layout.draw(c)
        c.restore()
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(154, 154, 173); textSize = 32f }
        c.drawText("✦ GemmaChat Hybrid • Gemma 4 E2B (offline)", pad, height - pad + 10f, footer)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "kartu-gemma-${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Bagikan kartu",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // ------------------------- Backup & Restore JSON -------------------------

    suspend fun backup(uri: Uri) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "GemmaChatHybrid")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("sessions", JSONArray().apply {
            db.sessions().all().forEach { s ->
                put(JSONObject().put("id", s.id).put("title", s.title).put("pinned", s.pinned)
                    .put("createdAt", s.createdAt).put("updatedAt", s.updatedAt))
            }
        })
        root.put("messages", JSONArray().apply {
            db.messages().all().forEach { m ->
                put(JSONObject().put("id", m.id).put("sessionId", m.sessionId).put("role", m.role).put("text", m.text)
                    .put("thought", m.thought).put("kind", m.kind).put("payload", m.payload)
                    .put("attachmentName", m.attachmentName ?: JSONObject.NULL)
                    .put("attachmentText", m.attachmentText ?: JSONObject.NULL)
                    .put("starred", m.starred).put("online", m.online).put("tokensPerSec", m.tokensPerSec)
                    .put("tokenCount", m.tokenCount).put("durationMs", m.durationMs).put("createdAt", m.createdAt))
            }
        })
        root.put("memories", JSONArray().apply {
            db.memories().all().forEach { put(JSONObject().put("fact", it.fact).put("createdAt", it.createdAt)) }
        })
        root.put("notes", JSONArray().apply {
            db.notes().all().forEach {
                put(JSONObject().put("title", it.title).put("text", it.text).put("createdAt", it.createdAt))
            }
        })
        root.put("settings", JSONObject().apply {
            settings.exportMap().forEach { (k, v) ->
                val type = when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; else -> "s" }
                put(k, JSONObject().put("t", type).put("v", v?.toString() ?: ""))
            }
        })
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) }
            ?: throw IllegalStateException("Tidak bisa menulis file backup")
    }

    /** Memulihkan data dari file backup. Data lama diganti seluruhnya. */
    suspend fun restore(uri: Uri): String = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("File tidak bisa dibaca")
        val root = JSONObject(text)
        require(root.optString("app") == "GemmaChatHybrid") { "Ini bukan file backup GemmaChat Hybrid." }

        db.messages().deleteAll()
        db.sessions().deleteAll()
        db.memories().deleteAll()
        db.notes().deleteAll()

        val idMap = HashMap<Long, Long>()
        val sessions = root.optJSONArray("sessions") ?: JSONArray()
        for (i in 0 until sessions.length()) {
            val o = sessions.getJSONObject(i)
            val newId = db.sessions().insert(
                SessionEntity(title = o.optString("title"), pinned = o.optBoolean("pinned"),
                    createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"))
            )
            idMap[o.getLong("id")] = newId
        }
        val msgs = root.optJSONArray("messages") ?: JSONArray()
        var count = 0
        for (i in 0 until msgs.length()) {
            val o = msgs.getJSONObject(i)
            val sid = idMap[o.getLong("sessionId")] ?: continue
            db.messages().insert(
                MessageEntity(
                    sessionId = sid, role = o.optString("role"), text = o.optString("text"),
                    thought = o.optString("thought"), kind = o.optString("kind", "text"), payload = o.optString("payload"),
                    attachmentName = o.optString("attachmentName").takeIf { !o.isNull("attachmentName") && it.isNotEmpty() },
                    attachmentText = o.optString("attachmentText").takeIf { !o.isNull("attachmentText") && it.isNotEmpty() },
                    starred = o.optBoolean("starred"), online = o.optBoolean("online"),
                    tokensPerSec = o.optDouble("tokensPerSec", 0.0), tokenCount = o.optInt("tokenCount"),
                    durationMs = o.optLong("durationMs"), createdAt = o.optLong("createdAt"),
                )
            )
            count++
        }
        val mems = root.optJSONArray("memories") ?: JSONArray()
        for (i in 0 until mems.length()) {
            val o = mems.getJSONObject(i)
            db.memories().insert(MemoryEntity(fact = o.optString("fact"), createdAt = o.optLong("createdAt")))
        }
        val notes = root.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until notes.length()) {
            val o = notes.getJSONObject(i)
            db.notes().insert(NoteEntity(title = o.optString("title"), text = o.optString("text"), createdAt = o.optLong("createdAt")))
        }
        root.optJSONObject("settings")?.let { s ->
            val map = HashMap<String, Any?>()
            s.keys().forEach { k ->
                val o = s.optJSONObject(k) ?: return@forEach
                val v = o.optString("v")
                map[k] = when (o.optString("t")) {
                    "b" -> v.toBoolean()
                    "i" -> v.toIntOrNull()
                    "l" -> v.toLongOrNull()
                    "f" -> v.toFloatOrNull()
                    else -> v
                }
            }
            // Path model & sesi terakhir spesifik perangkat → jangan dipulihkan.
            map.remove("selectedModelPath")
            map.remove("lastSession")
            settings.importMap(map)
        }
        "Dipulihkan: ${sessions.length()} sesi, $count pesan, ${mems.length()} memori, ${notes.length()} catatan."
    }
}
