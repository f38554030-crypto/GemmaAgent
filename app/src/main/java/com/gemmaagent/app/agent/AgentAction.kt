package com.gemmaagent.app.agent

import org.json.JSONObject

/** Satu perintah JSON dari AI. */
sealed class AgentAction {
    data class ClickText(val text: String) : AgentAction()
    data class TypeText(val text: String) : AgentAction()
    data class Swipe(val direction: String) : AgentAction()
    data class OpenApp(val name: String) : AgentAction()
    data object Back : AgentAction()
    data object Home : AgentAction()
    data object OpenNotifications : AgentAction()
    data class Done(val summary: String) : AgentAction()

    /** Label manusiawi untuk log & kartu aksi. */
    fun describe(): String = when (this) {
        is ClickText -> "Mengetuk \"$text\""
        is TypeText -> "Mengetik \"${text.take(40)}\""
        is Swipe -> "Menggeser layar ke ${arahId(direction)}"
        is OpenApp -> "Membuka aplikasi $name"
        Back -> "Menekan tombol Kembali"
        Home -> "Kembali ke layar Beranda"
        OpenNotifications -> "Membuka panel notifikasi"
        is Done -> "Selesai"
    }

    /** Kunci untuk mendeteksi aksi berulang (anti-looping). */
    fun key(): String = when (this) {
        is ClickText -> "click:${text.lowercase()}"
        is TypeText -> "type:${text.lowercase()}"
        is Swipe -> "swipe:${direction.lowercase()}"
        is OpenApp -> "open:${name.lowercase()}"
        Back -> "back"
        Home -> "home"
        OpenNotifications -> "notif"
        is Done -> "done"
    }

    private fun arahId(d: String) = when (d.lowercase()) {
        "up" -> "atas"; "down" -> "bawah"; "left" -> "kiri"; "right" -> "kanan"; else -> d
    }
}

data class ParsedStep(val thought: String, val action: AgentAction)

object ActionParser {

    /**
     * Mengambil objek JSON pertama dari jawaban model.
     * Model kecil kadang menambah teks/markdown di sekitar JSON, jadi kita cari kurung kurawalnya.
     */
    fun parse(raw: String): ParsedStep? {
        val json = extractJson(raw) ?: return null
        val obj = try {
            JSONObject(json)
        } catch (_: Throwable) {
            return null
        }
        val thought = obj.optString("thought", obj.optString("pikiran", "")).trim()
        val type = obj.optString("action", obj.optString("aksi", "")).trim().lowercase()
        fun str(vararg keys: String): String {
            for (k in keys) {
                val v = obj.optString(k, "").trim()
                if (v.isNotEmpty()) return v
            }
            return ""
        }
        val action: AgentAction = when (type) {
            "click_text", "click", "tap" -> AgentAction.ClickText(str("text", "target", "label").ifEmpty { return null })
            "type_text", "type" -> AgentAction.TypeText(str("text", "value").ifEmpty { return null })
            "swipe", "scroll" -> AgentAction.Swipe(str("direction", "dir", "text").ifEmpty { "up" })
            "open_app", "open" -> AgentAction.OpenApp(str("app", "name", "text").ifEmpty { return null })
            "back" -> AgentAction.Back
            "home" -> AgentAction.Home
            "open_notifications", "notifications" -> AgentAction.OpenNotifications
            "done", "finish", "reply", "answer" -> AgentAction.Done(
                str("summary", "text", "answer", "message").ifEmpty { thought.ifEmpty { "Tugas selesai." } }
            )
            else -> return null
        }
        return ParsedStep(thought, action)
    }

    private fun extractJson(raw: String): String? {
        val cleaned = raw.replace("```json", "").replace("```", "")
        val start = cleaned.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until cleaned.length) {
            val c = cleaned[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return cleaned.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
