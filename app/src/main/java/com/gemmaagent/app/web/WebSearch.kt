package com.gemmaagent.app.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.gemmaagent.app.data.SearchSource
import com.gemmaagent.app.data.kb.KnowledgeDb
import com.gemmaagent.app.data.kb.WebCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WebResult(val title: String, val url: String, val snippet: String, val content: String = "")

/** Hasil satu proses pencarian: sumber, langkah-langkah (untuk ditampilkan), dan apakah dari cache. */
data class WebContext(val query: String, val results: List<WebResult>, val steps: List<String>, val fromCache: Boolean)

/**
 * WebSearchEngine umum tanpa API key:
 * - DuckDuckGo HTML (html.duckduckgo.com) + Wikipedia API, opsional Google News RSS.
 * - Smart Web Reader: mengambil paragraf utama dari 1–2 tautan teratas, membuang iklan/HTML sampah.
 * - Hasil disimpan ke arsip lokal agar bisa dibaca saat offline.
 */
class WebSearchEngine(private val context: Context, private val kb: KnowledgeDb) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun get(url: String, ua: String = UA): String {
        val req = Request.Builder().url(url).header("User-Agent", ua).header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("Respons kosong")
            // Batasi 2 MB agar hemat RAM.
            val bytes = body.byteStream().use { it.readNBytesCompat(2 * 1024 * 1024) }
            return String(bytes, body.contentType()?.charset() ?: Charsets.UTF_8)
        }
    }

    /**
     * Mencari lalu membaca 1–2 halaman teratas. [onStep] dipanggil untuk setiap langkah (ditampilkan ke pengguna).
     */
    suspend fun searchAndRead(query: String, source: SearchSource, budgetChars: Int, onStep: (String) -> Unit): WebContext =
        withContext(Dispatchers.IO) {
            val steps = mutableListOf<String>()
            fun step(s: String) { steps += s; onStep(s) }
            val q = query.take(200)
            step("Mencari di web: \"${q.take(60)}\" (${source.label})")
            var results = runCatching {
                when (source) {
                    SearchSource.WEB -> duckDuckGo(q, 5)
                    SearchSource.WIKIPEDIA -> wikipedia(q, 4, "id").ifEmpty { wikipedia(q, 4, "en") }
                    SearchSource.NEWS -> googleNews(q, 5)
                }
            }.onFailure { step("Sumber utama gagal: ${it.message}") }.getOrDefault(emptyList())

            if (results.isEmpty() && source != SearchSource.WIKIPEDIA) {
                step("Mencoba Wikipedia…")
                results = runCatching { wikipedia(q, 4, "id").ifEmpty { wikipedia(q, 3, "en") } }.getOrDefault(emptyList())
            }
            if (results.isEmpty()) {
                step("Tidak ada hasil online.")
                return@withContext WebContext(q, emptyList(), steps, false)
            }
            step("Ditemukan ${results.size} hasil. Membaca halaman teratas…")

            // Smart Web Reader: baca 1–2 halaman teratas (Wikipedia & berita sudah berisi ringkasan).
            val perPage = (budgetChars * 0.35).toInt().coerceIn(600, 3000)
            val enriched = if (source == SearchSource.WEB) {
                coroutineScope {
                    results.mapIndexed { i, r ->
                        async {
                            if (i < 2) {
                                runCatching { readArticle(r.url, perPage) }.map { r.copy(content = it.snippet) }
                                    .getOrElse { r }
                            } else r
                        }
                    }.awaitAll()
                }
            } else results
            enriched.take(2).forEach { if (it.content.isNotBlank()) step("Membaca: ${hostOf(it.url)}") }

            // Simpan ke arsip offline.
            enriched.take(3).forEach { r ->
                runCatching {
                    if (kb.dao().countUrl(r.url) == 0) {
                        kb.dao().insertWeb(WebCacheEntity(query = q, url = r.url, title = r.title, content = (r.content.ifBlank { r.snippet }).take(6000)))
                    }
                }
            }
            step("Menyusun jawaban dari ${enriched.size} sumber")
            WebContext(q, enriched, steps, false)
        }

    /** Auto-fallback: cari di arsip lokal bila internet mati. */
    suspend fun searchCache(query: String, limit: Int = 3): List<WebResult> = withContext(Dispatchers.IO) {
        val match = KnowledgeDb.ftsQuery(query) ?: return@withContext emptyList()
        runCatching { kb.dao().searchWeb(match, limit) }.getOrDefault(emptyList())
            .map { WebResult(it.title, it.url, it.content.take(300), it.content) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun duckDuckGo(q: String, limit: Int): List<WebResult> {
        val doc = Jsoup.parse(get("https://html.duckduckgo.com/html/?q=${enc(q)}&kl=id-id"))
        return doc.select(".result").mapNotNull { el ->
            if (el.hasClass("result--ad")) return@mapNotNull null
            val a = el.selectFirst(".result__a") ?: return@mapNotNull null
            var href = a.attr("href")
            if (href.contains("uddg=")) href = URLDecoder.decode(href.substringAfter("uddg=").substringBefore("&"), "UTF-8")
            if (href.startsWith("//")) href = "https:$href"
            if (!href.startsWith("http") || href.contains("duckduckgo.com/y.js")) return@mapNotNull null
            WebResult(a.text(), href, el.selectFirst(".result__snippet")?.text().orEmpty())
        }.filter { it.title.isNotBlank() }.distinctBy { it.url }.take(limit)
    }

    private fun wikipedia(q: String, limit: Int, lang: String): List<WebResult> {
        val base = "https://$lang.wikipedia.org/w/api.php"
        val ua = "GemmaChatHybrid/3.0 (Android; aplikasi pribadi)"
        val hits = JSONObject(get("$base?action=query&list=search&format=json&srlimit=$limit&srsearch=${enc(q)}", ua))
            .optJSONObject("query")?.optJSONArray("search") ?: JSONArray()
        if (hits.length() == 0) return emptyList()
        val ids = (0 until hits.length()).joinToString("|") { hits.getJSONObject(it).getLong("pageid").toString() }
        val pages = JSONObject(get("$base?action=query&prop=extracts&exintro=1&explaintext=1&exchars=1200&format=json&pageids=$ids", ua))
            .optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
        return (0 until hits.length()).map { i ->
            val h = hits.getJSONObject(i)
            val title = h.getString("title")
            val extract = pages.optJSONObject(h.getLong("pageid").toString())?.optString("extract").orEmpty()
            WebResult(title, "https://$lang.wikipedia.org/wiki/${enc(title.replace(' ', '_'))}",
                extract.take(300).ifBlank { Jsoup.parse(h.optString("snippet")).text() }, extract)
        }
    }

    private fun googleNews(q: String, limit: Int): List<WebResult> {
        val doc = Jsoup.parse(get("https://news.google.com/rss/search?q=${enc(q)}&hl=id&gl=ID&ceid=ID:id"), "", Parser.xmlParser())
        return doc.select("item").take(limit).map { item ->
            val desc = Jsoup.parse(item.selectFirst("description")?.text().orEmpty()).text()
            val date = item.selectFirst("pubDate")?.text().orEmpty()
            WebResult(item.selectFirst("title")?.text().orEmpty(), item.selectFirst("link")?.text().orEmpty(),
                listOf(date, desc).filter { it.isNotBlank() }.joinToString(" — ").take(400))
        }
    }

    /** Smart Web Reader: teks bersih halaman (fitur 13). */
    suspend fun readArticle(url: String, maxChars: Int): WebResult = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(get(url), url)
        doc.select("script, style, noscript, nav, header, footer, aside, form, iframe, svg, button, figure, " +
            "[class*=ad-], [class*=ads], [id*=ads], [class*=banner], [class*=cookie], [class*=share], [class*=related], [class*=comment], [class*=newsletter]").remove()
        val main = doc.selectFirst("article") ?: doc.selectFirst("main") ?: doc.selectFirst("[role=main]") ?: doc.body()
        val paragraphs = main.select("h1, h2, h3, p, li").map { it.text().trim() }
            .filter { it.length > 40 && !it.contains("cookie", true) }.distinct()
        val sb = StringBuilder()
        for (p in paragraphs) {
            if (sb.length + p.length > maxChars) {
                // Potong cerdas di akhir kalimat.
                val room = maxChars - sb.length
                if (room > 120) sb.append(p.take(room).substringBeforeLast(". ", p.take(room))).append('.')
                break
            }
            sb.append(p).append('\n')
        }
        val text = sb.toString().ifBlank { main.text().take(maxChars) }
        runCatching {
            if (kb.dao().countUrl(url) == 0) kb.dao().insertWeb(WebCacheEntity(query = "URL", url = url, title = doc.title().ifBlank { url }, content = text.take(6000)))
        }
        WebResult(doc.title().ifBlank { url }, url, text, text)
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Linux; Android 13; POCO M4 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        private val urlRegex = Regex("https?://[^\\s<>\"')\\]]+", RegexOption.IGNORE_CASE)

        fun findUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ';', '!', '?')
        fun hostOf(url: String) = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

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
    }
}
