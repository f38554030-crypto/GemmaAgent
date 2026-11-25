package com.gemmaagent.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Accent(val label: String) { BLUE("Biru Elektrik"), EMERALD("Zamrud"), PURPLE("Ungu"), MONO("Monokrom") }

enum class FontScale(val label: String, val factor: Float) { SMALL("Kecil", 0.88f), NORMAL("Normal", 1f), LARGE("Besar", 1.18f) }

enum class SearchSource(val label: String) { WEB("Web Umum"), WIKIPEDIA("Wikipedia"), NEWS("Berita Terkini") }

data class AppSettings(
    // Mesin
    val cpuThreads: Int = 4,
    val contextTokens: Int = 1536,
    val maxOutputTokens: Int = 1536,
    val temperature: Float = 0.7f,
    val historyTurns: Int = 6,
    val selectedModelPath: String? = null,
    // Percakapan
    val thinking: Boolean = false,
    val online: Boolean = false,
    val searchSource: SearchSource = SearchSource.WEB,
    val personaId: String = "umum",
    val customSystemPrompt: String = "",
    val followUps: Boolean = true,
    val useMemory: Boolean = true,
    // Tampilan
    val accent: Accent = Accent.BLUE,
    val fontScale: FontScale = FontScale.NORMAL,
    // Keamanan & lainnya
    val biometricLock: Boolean = false,
    val onboardingDone: Boolean = false,
)

/** Pengaturan disimpan di SharedPreferences dan dipancarkan sebagai StateFlow. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        save(next)
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            cpuThreads = prefs.getInt("cpuThreads", d.cpuThreads).coerceIn(4, 6),
            contextTokens = prefs.getInt("contextTokens", d.contextTokens),
            maxOutputTokens = prefs.getInt("maxOutputTokens", d.maxOutputTokens),
            temperature = prefs.getFloat("temperature", d.temperature),
            historyTurns = prefs.getInt("historyTurns", d.historyTurns).coerceIn(5, 8),
            selectedModelPath = prefs.getString("selectedModelPath", null),
            thinking = prefs.getBoolean("thinking", d.thinking),
            online = prefs.getBoolean("online", d.online),
            searchSource = enumOr(prefs.getString("searchSource", null), d.searchSource),
            personaId = prefs.getString("personaId", d.personaId) ?: d.personaId,
            customSystemPrompt = prefs.getString("customSystemPrompt", "") ?: "",
            followUps = prefs.getBoolean("followUps", d.followUps),
            useMemory = prefs.getBoolean("useMemory", d.useMemory),
            accent = enumOr(prefs.getString("accent", null), d.accent),
            fontScale = enumOr(prefs.getString("fontScale", null), d.fontScale),
            biometricLock = prefs.getBoolean("biometricLock", d.biometricLock),
            onboardingDone = prefs.getBoolean("onboardingDone", d.onboardingDone),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putInt("cpuThreads", s.cpuThreads)
            .putInt("contextTokens", s.contextTokens)
            .putInt("maxOutputTokens", s.maxOutputTokens)
            .putFloat("temperature", s.temperature)
            .putInt("historyTurns", s.historyTurns)
            .putString("selectedModelPath", s.selectedModelPath)
            .putBoolean("thinking", s.thinking)
            .putBoolean("online", s.online)
            .putString("searchSource", s.searchSource.name)
            .putString("personaId", s.personaId)
            .putString("customSystemPrompt", s.customSystemPrompt)
            .putBoolean("followUps", s.followUps)
            .putBoolean("useMemory", s.useMemory)
            .putString("accent", s.accent.name)
            .putString("fontScale", s.fontScale.name)
            .putBoolean("biometricLock", s.biometricLock)
            .putBoolean("onboardingDone", s.onboardingDone)
            .apply()
    }

    // ---- Draf ketikan per sesi (fitur Auto-Save Draf) ----
    fun saveDraft(sessionId: Long?, text: String) {
        prefs.edit().putString("draft_${sessionId ?: 0}", text).apply()
    }

    fun loadDraft(sessionId: Long?): String = prefs.getString("draft_${sessionId ?: 0}", "") ?: ""

    fun lastSessionId(): Long? = prefs.getLong("lastSession", -1L).takeIf { it > 0 }
    fun setLastSessionId(id: Long?) {
        prefs.edit().putLong("lastSession", id ?: -1L).apply()
    }

    /** Semua pengaturan sebagai map (untuk backup JSON). */
    fun exportMap(): Map<String, Any?> = prefs.all.filterKeys { !it.startsWith("draft_") }

    fun importMap(map: Map<String, Any?>) {
        val e = prefs.edit()
        for ((k, v) in map) {
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is Double -> e.putFloat(k, v.toFloat())
                is String -> e.putString(k, v)
            }
        }
        e.apply()
        _settings.value = load()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
