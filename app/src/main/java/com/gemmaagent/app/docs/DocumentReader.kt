package com.gemmaagent.app.docs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class LoadedDocument(val name: String, val text: String, val truncated: Boolean)

/**
 * Pembaca dokumen PDF & TXT. Semua kerja berat berjalan di Dispatchers.IO.
 * - PDF dibaca per halaman dengan buffer memori kecil (sisanya ke file sementara) agar Java heap aman.
 * - Total teks dibatasi 60.000 karakter; potongan untuk AI maksimal 4.000 karakter.
 */
object DocumentReader {
    const val MAX_CHUNK_CHARS = 4000
    private const val MAX_CHARS = 60_000
    private const val MAX_PDF_PAGES = 60

    suspend fun read(context: Context, uri: Uri): LoadedDocument = withContext(Dispatchers.IO) {
        val name = displayName(context, uri) ?: "dokumen"
        val mime = context.contentResolver.getType(uri).orEmpty()
        val isPdf = name.lowercase().endsWith(".pdf") || mime == "application/pdf"
        val (raw, cut) = if (isPdf) readPdf(context, uri) else readText(context, uri)
        val clean = raw.replace("\r", "").replace(Regex("[ \t]+"), " ").replace(Regex("\n{3,}"), "\n\n").trim()
        LoadedDocument(name, clean.take(MAX_CHARS), cut || clean.length > MAX_CHARS)
    }

    private fun readText(context: Context, uri: Uri): Pair<String, Boolean> =
        context.contentResolver.openInputStream(uri)?.use { ins ->
            val bytes = ins.readNBytesCompat(MAX_CHARS * 4)
            String(bytes, Charsets.UTF_8) to (bytes.size >= MAX_CHARS * 4)
        } ?: ("" to false)

    private suspend fun readPdf(context: Context, uri: Uri): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val memory = MemoryUsageSetting.setupMixed(16L * 1024 * 1024).setTempDir(context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { ins ->
            PDDocument.load(ins, memory).use { doc ->
                val stripper = PDFTextStripper()
                val sb = StringBuilder()
                val pages = minOf(doc.numberOfPages, MAX_PDF_PAGES)
                var truncated = doc.numberOfPages > MAX_PDF_PAGES
                for (p in 1..pages) {
                    coroutineContext.ensureActive()
                    stripper.startPage = p
                    stripper.endPage = p
                    sb.append(stripper.getText(doc)).append('\n')
                    if (sb.length >= MAX_CHARS) { truncated = true; break }
                }
                sb.toString() to truncated
            }
        } ?: ("" to false)
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (total < max) {
            val r = read(buf, 0, minOf(buf.size, max - total))
            if (r < 0) break
            out.write(buf, 0, r)
            total += r
        }
        return out.toByteArray()
    }

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Pemecahan teks hemat RAM: potong per paragraf, setiap potongan maksimal [size] (≤ 4.000) karakter. */
    fun chunk(text: String, size: Int): List<String> {
        val limit = size.coerceIn(200, MAX_CHUNK_CHARS)
        if (text.length <= limit) return listOf(text)
        val chunks = mutableListOf<String>()
        val sb = StringBuilder()
        for (para in text.split("\n")) {
            if (sb.length + para.length + 1 > limit && sb.isNotEmpty()) {
                chunks += sb.toString()
                sb.clear()
            }
            if (para.length > limit) {
                para.chunked(limit).forEach { chunks += it }
            } else {
                sb.append(para).append('\n')
            }
        }
        if (sb.isNotBlank()) chunks += sb.toString()
        return chunks
    }
}
