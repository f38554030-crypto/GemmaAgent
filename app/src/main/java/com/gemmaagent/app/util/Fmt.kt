package com.gemmaagent.app.util

import java.util.Locale

/**
 * Format angka yang aman. Jangan pernah menyisipkan teks bebas (nama file, persen, dll.)
 * ke dalam pola String.format — karakter "%" di dalamnya bisa membuat aplikasi crash.
 */
object Fmt {
    private val ID = Locale("id", "ID")

    fun gb(bytes: Long): String = String.format(ID, "%.2f", bytes / (1024.0 * 1024 * 1024))

    fun gbFromMb(mb: Long): String = String.format(ID, "%.1f", mb / 1024.0)
}
