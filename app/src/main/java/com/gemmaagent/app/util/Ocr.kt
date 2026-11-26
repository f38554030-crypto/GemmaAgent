package com.gemmaagent.app.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR offline (fitur 17) memakai ML Kit Text Recognition versi bundled (model ikut di APK, tanpa internet).
 * Dimuat hanya saat dipakai (lazy).
 */
object Ocr {
    suspend fun read(context: Context, uri: Uri): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    recognizer.close()
                    if (cont.isActive) cont.resume(result.text.trim())
                }
                .addOnFailureListener { e ->
                    recognizer.close()
                    if (cont.isActive) cont.resumeWithException(e)
                }
        } catch (t: Throwable) {
            recognizer.close()
            if (cont.isActive) cont.resumeWithException(t)
        }
    }
}
