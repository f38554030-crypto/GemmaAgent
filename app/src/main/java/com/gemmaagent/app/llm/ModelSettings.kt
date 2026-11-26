package com.gemmaagent.app.llm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Jenis mesin untuk sebuah file model. */
enum class EngineType(val badge: String) { GGUF("GGUF"), LITERT("LITERT-LM") }

/** Pilihan chat template (khusus GGUF; LiteRT-LM memakai template bawaan file). */
enum class ChatTemplate(val label: String) {
    AUTO("Bawaan model (otomatis)"),
    GEMMA("Gemma"),
    CHATML("ChatML"),
    ALPACA("Alpaca"),
    RAW("Raw (tanpa template)"),
}

/** Pengaturan parameter per model (fitur 3). */
data class ModelSettings(
    val template: ChatTemplate = ChatTemplate.AUTO,
    val temperature: Float = 0.7f,
    val repeatPenalty: Float = 1.1f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val nCtx: Int = 2048,
    val maxTokens: Int = 768,
    val stopWords: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("template", template.name)
        .put("temperature", temperature.toDouble())
        .put("repeatPenalty", repeatPenalty.toDouble())
        .put("topP", topP.toDouble())
        .put("topK", topK)
        .put("nCtx", nCtx)
        .put("maxTokens", maxTokens)
        .put("stopWords", JSONArray(stopWords))

    companion object {
        fun fromJson(o: JSONObject?): ModelSettings {
            if (o == null) return ModelSettings()
            val d = ModelSettings()
            val stops = o.optJSONArray("stopWords")
            return ModelSettings(
                template = runCatching { ChatTemplate.valueOf(o.optString("template")) }.getOrDefault(d.template),
                temperature = o.optDouble("temperature", d.temperature.toDouble()).toFloat().coerceIn(0.05f, 1.5f),
                repeatPenalty = o.optDouble("repeatPenalty", d.repeatPenalty.toDouble()).toFloat().coerceIn(1f, 2f),
                topP = o.optDouble("topP", d.topP.toDouble()).toFloat().coerceIn(0.05f, 1f),
                topK = o.optInt("topK", d.topK).coerceIn(1, 200),
                nCtx = o.optInt("nCtx", d.nCtx).coerceIn(512, 8192),
                maxTokens = o.optInt("maxTokens", d.maxTokens).coerceIn(64, 4096),
                stopWords = if (stops == null) emptyList() else List(stops.length()) { stops.optString(it) }.filter { it.isNotEmpty() },
            )
        }

        /** Nilai awal yang aman untuk RAM 8 GB. */
        fun defaultsFor(type: EngineType) = if (type == EngineType.LITERT) ModelSettings(nCtx = 1536, maxTokens = 768) else ModelSettings()
    }
}

/** Menyimpan pengaturan tiap model berdasarkan path file. */
class ModelSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("model_settings", Context.MODE_PRIVATE)

    fun get(path: String, type: EngineType): ModelSettings {
        val raw = prefs.getString(path, null) ?: return ModelSettings.defaultsFor(type)
        return ModelSettings.fromJson(runCatching { JSONObject(raw) }.getOrNull())
    }

    fun put(path: String, settings: ModelSettings) {
        prefs.edit().putString(path, settings.toJson().toString()).apply()
    }
}

/** Template chat buatan sendiri (dipakai bila template bawaan model tidak bisa dibaca). */
object PromptFormatter {
    fun format(template: ChatTemplate, system: String, history: List<ChatTurn>, user: String): String {
        val sb = StringBuilder()
        when (template) {
            ChatTemplate.AUTO -> return format(ChatTemplate.GEMMA, system, history, user)
            ChatTemplate.CHATML -> {
                if (system.isNotBlank()) sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
                history.forEach { sb.append("<|im_start|>").append(if (it.user) "user" else "assistant").append('\n').append(it.text).append("<|im_end|>\n") }
                sb.append("<|im_start|>user\n").append(user).append("<|im_end|>\n<|im_start|>assistant\n")
            }
            ChatTemplate.GEMMA -> {
                // Gemma tidak punya peran "system": gabungkan ke giliran pengguna pertama.
                var first = true
                fun userTurn(text: String) {
                    sb.append("<start_of_turn>user\n")
                    if (first && system.isNotBlank()) sb.append(system).append("\n\n")
                    first = false
                    sb.append(text).append("<end_of_turn>\n")
                }
                history.forEach { if (it.user) userTurn(it.text) else sb.append("<start_of_turn>model\n").append(it.text).append("<end_of_turn>\n") }
                userTurn(user)
                sb.append("<start_of_turn>model\n")
            }
            ChatTemplate.ALPACA -> {
                if (system.isNotBlank()) sb.append(system).append("\n\n")
                history.forEach {
                    if (it.user) sb.append("### Instruction:\n").append(it.text).append("\n\n")
                    else sb.append("### Response:\n").append(it.text).append("\n\n")
                }
                sb.append("### Instruction:\n").append(user).append("\n\n### Response:\n")
            }
            ChatTemplate.RAW -> {
                if (system.isNotBlank()) sb.append(system).append("\n\n")
                history.forEach { sb.append(if (it.user) "User: " else "Assistant: ").append(it.text).append('\n') }
                sb.append("User: ").append(user).append("\nAssistant:")
            }
        }
        return sb.toString()
    }

    /** Stop words standar per template (selain token EOG dari model). */
    fun defaultStops(template: ChatTemplate): List<String> = when (template) {
        ChatTemplate.GEMMA -> listOf("<end_of_turn>", "<start_of_turn>")
        ChatTemplate.CHATML -> listOf("<|im_end|>", "<|im_start|>")
        ChatTemplate.ALPACA -> listOf("### Instruction:")
        ChatTemplate.RAW -> listOf("\nUser:")
        ChatTemplate.AUTO -> emptyList()
    }
}
