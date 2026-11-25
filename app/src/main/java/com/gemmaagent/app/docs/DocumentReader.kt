package com.gemmaagent.app.docs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LoadedDocument(val name: String, val text: String, val truncated: Boolean)

/** Pembaca dokumen PDF & TXT (fitur 8) dengan batas aman agar tidak OutOfMemory. */
object DocumentReader {
    private const val MAX_CHARS = 60_000
    private const val MAX_PDF_PAGES = 40

    suspend fun read(context: Context, uri: Uri): LoadedDocument = withContext(Dispatchers.IO) {
        val name = displayName(context, uri) ?: "dokumen"
        val lower = name.lowercase()
        val mime = context.contentResolver.getType(uri).orEmpty()
        val isPdf = lower.endsWith(".pdf") || mime == "application/pdf"
        val raw = if (isPdf) readPdf(context, uri) else readText(context, uri)
        val clean = raw.replace("\r", "").replace(Regex("[ \t]+"), " ").replace(Regex("\n{3,}"), "\n\n").trim()
        LoadedDocument(name, clean.take(MAX_CHARS), clean.length > MAX_CHARS)
    }

    private fun readText(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { ins ->
            val bytes = ins.readNBytesCompat(MAX_CHARS * 4)
            String(bytes, Charsets.UTF_8)
        } ?: ""

    private fun readPdf(context: Context, uri: Uri): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        return context.contentResolver.openInputStream(uri)?.use { ins ->
            PDDocument.load(ins).use { doc ->
                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = minOf(doc.numberOfPages, MAX_PDF_PAGES)
                stripper.getText(doc)
            }
        } ?: ""
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

    /** Pemecahan teks hemat RAM (fitur 9): potong per paragraf dengan ukuran maksimal [size] karakter. */
    fun chunk(text: String, size: Int): List<String> {
        if (text.length <= size) return listOf(text)
        val chunks = mutableListOf<String>()
        val sb = StringBuilder()
        for (para in text.split("\n")) {
            if (sb.length + para.length + 1 > size && sb.isNotEmpty()) {
                chunks += sb.toString()
                sb.clear()
            }
            if (para.length > size) {
                para.chunked(size).forEach { chunks += it }
            } else {
                sb.append(para).append('\n')
            }
        }
        if (sb.isNotBlank()) chunks += sb.toString()
        return chunks
    }
}
