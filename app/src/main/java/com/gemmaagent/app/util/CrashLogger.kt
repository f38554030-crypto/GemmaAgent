package com.gemmaagent.app.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menyimpan pesan error terakhir jika aplikasi sempat crash, supaya bisa
 * ditampilkan di Beranda saat aplikasi dibuka lagi (mudah di-screenshot).
 */
object CrashLogger {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(appContext.filesDir, FILE).writeText("[$time] thread=${thread.name}\n$sw".take(6000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    fun record(context: Context, title: String, error: Throwable) {
        runCatching {
            val sw = StringWriter()
            error.printStackTrace(PrintWriter(sw))
            File(context.filesDir, FILE).writeText("$title\n$sw".take(6000))
        }
    }
}
