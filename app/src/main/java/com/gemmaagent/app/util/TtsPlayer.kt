package com.gemmaagent.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Pemutar suara MANUAL (fitur 42): hanya berbicara bila pengguna menekan ikon speaker.
 * Tidak ada pembacaan otomatis (Mode Hening Total).
 */
class TtsPlayer(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<Long, String>? = null

    private val _speakingId = MutableStateFlow<Long?>(null)
    val speakingId: StateFlow<Long?> = _speakingId.asStateFlow()

    private fun ensure() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("id", "ID"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts?.setLanguage(Locale.getDefault())
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { _speakingId.value = null }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { _speakingId.value = null }
                })
                ready = true
                pending?.let { (id, text) -> speakNow(id, text) }
                pending = null
            }
        }
    }

    fun toggle(id: Long, text: String) {
        if (_speakingId.value == id) {
            stop()
            return
        }
        ensure()
        if (ready) speakNow(id, text) else pending = id to text
    }

    private fun speakNow(id: Long, text: String) {
        val clean = text.replace(Regex("[*#`>|_~]"), " ").replace(Regex("\\[(\\d+)]"), "").take(3900)
        _speakingId.value = id
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "msg_$id")
    }

    fun stop() {
        runCatching { tts?.stop() }
        _speakingId.value = null
    }
}
