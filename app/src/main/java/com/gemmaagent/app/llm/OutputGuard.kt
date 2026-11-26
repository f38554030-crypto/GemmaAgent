package com.gemmaagent.app.llm

import com.gemmaagent.app.data.GgufReader
import java.io.File
import java.io.RandomAccessFile

/**
 * Pembersih output real-time.
 *
 * Model .gguf hasil fine-tuning kadang menulis token teknis sebagai teks biasa
 * (mis. `<end_of_turn>`, `<|im_end|>`, `<|eot_id|>`, `</s>`) lalu terus "mengobrol sendiri".
 * Filter ini:
 *  1. Memotong teks tepat di token akhir-giliran pertama dan memberi sinyal STOP.
 *  2. Menahan sementara ekor teks yang masih berupa awalan token teknis (mis. "<end_of")
 *     supaya potongan token tidak sempat berkedip di layar.
 *  3. Membuang token sisa yang tidak bermakna (<pad>, <unk>, <bos>, <mask>).
 */
class OutputCleaner(extraStops: List<String> = emptyList()) {

    private val stops: List<String> = (DEFAULT_STOPS + extraStops).map { it.trim('\n') }.filter { it.isNotEmpty() }.distinct()
    private val raw = StringBuilder()

    var stopped = false
        private set

    /** Teks yang aman ditampilkan sejauh ini. */
    var visible: String = ""
        private set

    fun feed(piece: String): String {
        if (stopped || piece.isEmpty()) return visible
        raw.append(piece)
        val s = raw.toString()

        // 1) Token akhir-giliran → potong & berhenti.
        var cut = -1
        for (st in stops) {
            val i = s.indexOf(st)
            if (i >= 0 && (cut < 0 || i < cut)) cut = i
        }
        PATTERN_STOP.find(s)?.let { m -> if (cut < 0 || m.range.first < cut) cut = m.range.first }
        if (cut >= 0) {
            raw.setLength(cut)
            stopped = true
            visible = clean(raw.toString())
            return visible
        }

        // 2) Tahan ekor yang mungkin awal sebuah token teknis.
        var hold = 0
        val lastLt = s.lastIndexOf('<')
        if (lastLt >= 0 && s.length - lastLt <= MAX_TOKEN_LEN && s.indexOf('>', lastLt) < 0) {
            val tail = s.substring(lastLt)
            if (stops.any { it.startsWith(tail) } || PARTIAL_SPECIAL.matches(tail)) hold = tail.length
        }
        for (st in stops) {
            if (st.startsWith("<")) continue
            for (k in minOf(st.length - 1, s.length) downTo 1) {
                if (s.endsWith(st.substring(0, k))) { hold = maxOf(hold, k); break }
            }
        }
        visible = clean(s.substring(0, s.length - hold))
        return visible
    }

    /** Teks akhir setelah generasi selesai. */
    fun finish(): String = clean(raw.toString())

    private fun clean(t: String): String {
        var out = t
        for (junk in STRIP) if (out.contains(junk)) out = out.replace(junk, "")
        return out
    }

    companion object {
        private const val MAX_TOKEN_LEN = 24

        val DEFAULT_STOPS = listOf(
            "<end_of_turn>", "<start_of_turn>",
            "<|im_end|>", "<|im_start|>",
            "<|eot_id|>", "<|end_of_text|>", "<|start_header_id|>",
            "<|endoftext|>", "<|end|>", "<|user|>", "<|assistant|>",
            "</s>", "<eos>",
        )

        /** Pola umum token khusus ber-pipa, mis. <|eot_id|>, <|turn_end|>. */
        private val PATTERN_STOP = Regex("<\\|[A-Za-z0-9_]{2,20}\\|>")

        /** Awalan token khusus yang belum lengkap, mis. "<|eo" atau "<end_of_tu". */
        private val PARTIAL_SPECIAL = Regex("<\\|?[A-Za-z0-9_]{0,20}\\|?")

        private val STRIP = listOf("<pad>", "<unk>", "<bos>", "<mask>")
    }
}

/**
 * Penjaga anti-looping: menghentikan model yang mengulang baris / potongan teks yang sama terus-menerus.
 */
class LoopGuard {
    private var lastCheckedLen = 0

    /** Mengembalikan panjang teks yang dipertahankan bila terdeteksi loop, atau null bila aman. */
    fun check(text: String): Int? {
        if (text.length - lastCheckedLen < 48) return null
        lastCheckedLen = text.length

        // a) Baris non-kosong yang sama berulang ≥ 4 kali berturut-turut.
        val lines = text.trimEnd().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 4) {
            val last = lines.last()
            if (last.length >= 4 && lines.takeLast(4).all { it == last }) {
                val firstIdx = text.indexOf(last)
                return (firstIdx + last.length).coerceAtMost(text.length)
            }
        }
        // b) Potongan ekor berperiode p (12–250 karakter) yang berulang ≥ 3 kali berturut-turut.
        val n = text.length
        for (p in 12..minOf(250, n / 3)) {
            if (text.regionMatches(n - p, text, n - 2 * p, p) && text.regionMatches(n - 2 * p, text, n - 3 * p, p)) {
                val unit = text.substring(n - p)
                // Abaikan garis pemisah seperti "-----" / "=====" (variasi karakter sangat sedikit).
                if (unit.toSet().size > 3 && unit.any { it.isLetter() }) return n - 2 * p
            }
        }
        return null
    }
}

/** Validasi header file model SEBELUM dimuat ke mesin native. */
object ModelValidator {

    private val cache = HashMap<String, String?>()

    /** null = valid; selain itu pesan error yang mudah dipahami. */
    @Synchronized
    fun check(path: String, type: EngineType): String? {
        val f = File(path)
        val key = "$path|${f.length()}|${f.lastModified()}"
        if (cache.containsKey(key)) return cache[key]
        val result = runCatching { validate(f, type) }.getOrElse { "Header file tidak bisa dibaca: ${it.message}" }
        cache[key] = result
        return result
    }

    private fun validate(f: File, type: EngineType): String? {
        if (!f.exists()) return "File model tidak ditemukan: ${f.name}"
        if (!f.canRead()) return "File model tidak bisa dibaca. Izinkan Akses Semua File di Model Hub."
        if (f.length() < 50L * 1024 * 1024) return "Ukuran ${f.name} terlalu kecil (${f.length() / 1024} KB). Kemungkinan unduhan belum selesai."

        val head = ByteArray(16)
        RandomAccessFile(f, "r").use { it.readFully(head) }
        val headText = String(head, Charsets.ISO_8859_1)
        if (headText.trimStart().startsWith("<!DOCTYPE", true) || headText.trimStart().startsWith("<html", true)) {
            return "${f.name} berisi halaman web, bukan model. Unduhan dari Drive gagal (file belum dibagikan publik?)."
        }
        return when (type) {
            EngineType.GGUF -> {
                if (!headText.startsWith("GGUF")) return "${f.name} bukan file GGUF yang valid (magic header tidak cocok)."
                val version = (head[4].toInt() and 0xFF) or ((head[5].toInt() and 0xFF) shl 8) or
                    ((head[6].toInt() and 0xFF) shl 16) or ((head[7].toInt() and 0xFF) shl 24)
                if (version !in 1..20) return "Versi GGUF tidak dikenal ($version)."
                val md = GgufReader.read(f)
                if (md.architecture.isNullOrBlank()) return "Header GGUF tidak berisi arsitektur model."
                if ((md.tensorCount ?: 0) <= 0) return "Header GGUF tidak berisi tensor apa pun."
                null
            }
            EngineType.LITERT -> {
                val isTask = f.name.endsWith(".task", true)
                when {
                    isTask && !(head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) -> "${f.name} bukan paket .task yang valid."
                    !isTask && !headText.startsWith("LITERTLM") -> "${f.name} bukan file .litertlm yang valid (magic header tidak cocok)."
                    else -> null
                }
            }
        }
    }
}
