package com.gemmaagent.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gemmaagent.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Mengunduh file model dari link Google Drive di latar belakang.
 * - Ditulis ke "<nama>.tmp" dulu; baru di-rename ke nama asli setelah 100% & header valid.
 * - Buffer 64 KB, progres di notifikasi senyap (tanpa suara/getar).
 * - Melanjutkan unduhan .tmp yang terputus bila server mendukung Range.
 */
class DriveDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext fail("URL kosong")
        val target = File(inputData.getString(KEY_TARGET) ?: return@withContext fail("Tujuan kosong"))
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        runCatching { setForeground(foregroundInfo(0, target.name)) }

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        try {
            var existing = if (tmp.exists()) tmp.length() else 0L
            val reqBuilder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            if (existing > 0) reqBuilder.header("Range", "bytes=$existing-")
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.code == 416) {
                    // Range tidak valid (file .tmp lama tidak cocok) → hapus & ulangi dari awal.
                    tmp.delete()
                    return@withContext Result.retry()
                }
                if (!resp.isSuccessful) return@withContext fail("Server Drive menjawab HTTP ${resp.code}.")
                val body = resp.body ?: return@withContext fail("Respons kosong dari Drive.")
                val type = body.contentType()?.toString().orEmpty()
                if (type.startsWith("text/html")) {
                    return@withContext fail("Drive mengirim halaman web, bukan file. Pastikan file dibagikan \"Siapa saja yang memiliki link\".")
                }
                val append = resp.code == 206 && existing > 0
                if (!append) existing = 0
                val length = body.contentLength()
                val total = if (length > 0) existing + length else -1L

                var done = existing
                var lastPct = -1
                body.byteStream().use { input ->
                    FileOutputStream(tmp, append).buffered(BUFFER).use { out ->
                        val buf = ByteArray(BUFFER)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) ((done * 100) / total).toInt() else 0
                            if (pct != lastPct) {
                                lastPct = pct
                                setProgress(workDataOf(KEY_PERCENT to pct, KEY_DONE to done, KEY_TOTAL to total))
                                runCatching { setForeground(foregroundInfo(pct, target.name)) }
                            }
                        }
                    }
                }
                if (total > 0 && done < total) return@withContext fail("Unduhan terputus di ${done * 100 / total}%. Coba lagi untuk melanjutkan.")
            }

            // Validasi header sebelum dianggap selesai.
            headerProblem(tmp, target.name)?.let { problem ->
                tmp.delete()
                return@withContext fail(problem)
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) return@withContext fail("Gagal mengganti nama file .tmp.")
            Result.success(workDataOf(KEY_TARGET to target.absolutePath))
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            // .tmp sengaja disimpan agar bisa dilanjutkan.
            fail("Unduhan gagal: ${t.message ?: t.javaClass.simpleName}. File .tmp disimpan untuk dilanjutkan.")
        }
    }

    private fun headerProblem(f: File, name: String): String? {
        if (f.length() < 1024) return "File hasil unduhan terlalu kecil — kemungkinan bukan model."
        val head = ByteArray(8)
        RandomAccessFile(f, "r").use { it.readFully(head) }
        val text = String(head, Charsets.ISO_8859_1)
        return when {
            text.startsWith("<!") || text.startsWith("<htm", true) -> "Drive mengirim halaman web, bukan file model."
            name.endsWith(".gguf", true) && !text.startsWith("GGUF") -> "File unduhan bukan GGUF yang valid."
            name.endsWith(".litertlm", true) && !text.startsWith("LITERTLM") -> "File unduhan bukan .litertlm yang valid."
            else -> null
        }
    }

    private fun fail(msg: String) = Result.failure(workDataOf(KEY_ERROR to msg))

    private fun foregroundInfo(pct: Int, name: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Unduh model", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null); enableVibration(false)
                }
            )
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Mengunduh $name")
            .setContentText("$pct%")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, n)
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_TARGET = "target"
        const val KEY_PERCENT = "percent"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val BUFFER = 64 * 1024
        private const val CHANNEL = "model_download"
        private const val NOTIF_ID = 4343
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
