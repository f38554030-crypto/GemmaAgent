package com.gemmaagent.app.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Getaran (haptic) dan suara:
 * - Getar halus saat tombol utama / STOP ditekan.
 * - Text-to-Speech + nada beep singkat saat tugas selesai.
 */
class FeedbackManager(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale("id", "ID"))
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                ttsReady = true
            }
        }
    }

    fun warmUp() = main.post { ensureTts() }

    private fun vibrate(ms: Long, amplitude: Int = 120) {
        val v = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }

    fun clickHaptic() = vibrate(18, 80)
    fun stopHaptic() = vibrate(60, 255)
    fun warnHaptic() = vibrate(120, 200)

    private fun beep() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            main.postDelayed({ tg.release() }, 400)
        }
    }

    /** Dipanggil saat agen menyelesaikan seluruh perintah. */
    fun taskDone(summary: String, speak: Boolean) {
        main.post {
            vibrate(80, 160)
            beep()
            if (speak) {
                ensureTts()
                val text = "Tugas selesai. " + summary.take(160)
                main.postDelayed({
                    if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "done")
                }, if (ttsReady) 300 else 1200)
            }
        }
    }

    fun taskEnded() {
        main.post { vibrate(40, 120) }
    }
}
