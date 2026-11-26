package com.gemmaagent.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
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
import java.io.File

/**
 * Menyalin file model besar dari sumber cloud (mis. Google Drive) di latar belakang
 * dengan buffer 64 KB + progres, agar aplikasi tidak force close walau file 3+ GB.
 */
class ModelCopyWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uri = Uri.parse(inputData.getString(KEY_URI) ?: return@withContext fail("URI kosong"))
        val target = File(inputData.getString(KEY_TARGET) ?: return@withContext fail("Tujuan kosong"))
        val total = inputData.getLong(KEY_SIZE, -1L)
        runCatching { setForeground(foregroundInfo(0, target.name)) }

        val part = File(target.parentFile, target.name + ".tmp")
        try {
            target.parentFile?.mkdirs()
            val input = applicationContext.contentResolver.openInputStream(uri) ?: return@withContext fail("File tidak bisa dibuka")
            var copied = 0L
            var lastPct = -1
            input.use { ins ->
                part.outputStream().buffered(BUFFER).use { out ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        val pct = if (total > 0) ((copied * 100) / total).toInt() else 0
                        if (pct != lastPct) {
                            lastPct = pct
                            setProgress(workDataOf(KEY_PERCENT to pct, KEY_COPIED to copied, KEY_TOTAL to total))
                            runCatching { setForeground(foregroundInfo(pct, target.name)) }
                        }
                    }
                }
            }
            if (total > 0 && copied != total) return@withContext fail("Salinan tidak lengkap (${copied}/${total} byte)")
            target.delete()
            if (!part.renameTo(target)) return@withContext fail("Gagal menyimpan file")
            Result.success(workDataOf(KEY_TARGET to target.absolutePath))
        } catch (t: Throwable) {
            part.delete()
            if (t is kotlinx.coroutines.CancellationException) throw t
            fail(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun fail(msg: String) = Result.failure(workDataOf(KEY_ERROR to msg))

    private fun foregroundInfo(pct: Int, name: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Salin model", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) })
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Menyalin $name")
            .setContentText("$pct%")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, n)
    }

    companion object {
        const val KEY_URI = "uri"
        const val KEY_TARGET = "target"
        const val KEY_SIZE = "size"
        const val KEY_PERCENT = "percent"
        const val KEY_COPIED = "copied"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val BUFFER = 64 * 1024
        private const val CHANNEL = "model_copy"
        private const val NOTIF_ID = 4242
    }
}
