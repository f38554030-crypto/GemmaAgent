package com.gemmaagent.app.agent

/**
 * Safety Guard: mendeteksi tombol berisiko tinggi.
 * Agen WAJIB meminta persetujuan pengguna sebelum mengetuk tombol-tombol ini.
 */
object SafetyGuard {
    private val riskyWords = listOf(
        "transfer", "bayar", "pembayaran", "kirim", "hapus", "beli", "checkout",
        "pay", "send", "delete", "buy", "purchase", "top up", "topup", "konfirmasi pembayaran",
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
