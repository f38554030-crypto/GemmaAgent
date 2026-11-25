package com.gemmaagent.app.agent

/**
 * Safety Guard: mendeteksi tombol berisiko tinggi.
 * Agen WAJIB meminta persetujuan pengguna sebelum mengetuk tombol-tombol ini.
 */
object SafetyGuard {
    // Hanya kata berisiko TINGGI (uang / data hilang). "Kirim" sengaja TIDAK dimasukkan
    // agar agen bisa mengirim chat WhatsApp tanpa terhenti terus.
    // Padanan bahasa Inggris disertakan untuk aplikasi yang tampil dalam bahasa Inggris.
    private val riskyWords = listOf(
        "transfer",
        "bayar", "pembayaran",
        "beli",
        "hapus",
        "checkout",
        "pay", "buy", "purchase", "delete",
    )

    /** Mengembalikan kata berisiko yang ditemukan, atau null jika aman. */
    fun riskyWord(vararg labels: String?): String? {
        for (label in labels) {
            val l = label?.lowercase() ?: continue
            riskyWords.firstOrNull { w -> Regex("(^|[^\\p{L}])${Regex.escape(w)}").containsMatchIn(l) }
                ?.let { return it }
        }
        return null
    }
}
