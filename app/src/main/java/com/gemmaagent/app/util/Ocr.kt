package com.gemmaagent.app.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR offline (ML Kit, model ikut di APK). Menerima bitmap yang SUDAH diperkecil (maks 1024×1024)
 * oleh [BitmapUtil], sehingga tidak ada lonjakan memori dari foto kamera beresolusi penuh.
 */
object Ocr {
    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        cont.invokeOnCancellation { runCatching { recognizer.close() } }
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
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
