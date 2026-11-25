package com.gemmaagent.app.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.gemmaagent.app.data.SearchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLDecoder
import java.net.URLEncoder

data class WebResult(val title: String, val url: String, val snippet: String)

/**
 * Pencarian web publik tanpa API key:
 * - Web Umum: scraping ringan DuckDuckGo HTML.
 * - Wikipedia: API resmi Wikipedia (id, lalu en).
 * - Berita Terkini: RSS Google News (bahasa Indonesia).
 * Juga pembaca artikel dari URL (teks bersih).
 */
object WebSearch {
    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val TIMEOUT = 12_000

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private val urlRegex = Regex("https?://[^\\s<>\"')\\]]+", RegexOption.IGNORE_CASE)

    fun findUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ';', '!', '?')

    suspend fun search(query: String, source: SearchSource, limit: Int = 4): List<WebResult> = withContext(Dispatchers.IO) {
        val q = query.take(200)
        val primary = runCatching {
            when (source) {
                SearchSource.WEB -> duckDuckGo(q, limit)
                SearchSource.WIKIPEDIA -> wikipedia(q, limit, "id").ifEmpty { wikipedia(q, limit, "en") }
                SearchSource.NEWS -> googleNews(q, limit)
            }
        }.getOrDefault(emptyList())
        if (primary.isNotEmpty()) return@withContext primary
        // Cadangan: jika sumber utama gagal/diblokir, coba Wikipedia.
        if (source != SearchSource.WIKIPEDIA) {
            runCatching { wikipedia(q, limit, "id") }.getOrDefault(emptyList())
        } else emptyList()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun duckDuckGo(q: String, limit: Int): List<WebResult> {
        val doc = Jsoup.connect("https://html.duckduckgo.com/html/?q=${enc(q)}&kl=id-id")
            .userAgent(UA).timeout(TIMEOUT).get()
        return doc.select(".result").mapNotNull { el ->
            val a = el.selectFirst(".result__a") ?: return@mapNotNull null
            var href = a.attr("href")
            if (href.contains("uddg=")) {
                href = URLDecoder.decode(href.substringAfter("uddg=").substringBefore("&"), "UTF-8")
            }
            if (href.startsWith("//")) href = "https:$href"
            if (href.contains("duckduckgo.com/y.js")) return@mapNotNull null // iklan
            val snippet = el.selectFirst(".result__snippet")?.text().orEmpty()
            WebResult(a.text(), href, snippet)
        }.filter { it.title.isNotBlank() }.take(limit)
    }

    private fun wikipedia(q: String, limit: Int, lang: String): List<WebResult> {
        val base = "https://$lang.wikipedia.org/w/api.php"
        val searchJson = Jsoup.connect("$base?action=query&list=search&format=json&srlimit=$limit&srsearch=${enc(q)}")
            .userAgent("GemmaChatHybrid/2.0 (Android)").ignoreContentType(true).timeout(TIMEOUT).execute().body()
        val hits = JSONObject(searchJson).optJSONObject("query")?.optJSONArray("search") ?: JSONArray()
        if (hits.length() == 0) return emptyList()
        val ids = (0 until hits.length()).joinToString("|") { hits.getJSONObject(it).getLong("pageid").toString() }
        val extractJson = Jsoup.connect("$base?action=query&prop=extracts&exintro=1&explaintext=1&exchars=600&format=json&pageids=$ids")
            .userAgent("GemmaChatHybrid/2.0 (Android)").ignoreContentType(true).timeout(TIMEOUT).execute().body()
        val pages = JSONObject(extractJson).optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
        return (0 until hits.length()).map { i ->
            val h = hits.getJSONObject(i)
            val id = h.getLong("pageid").toString()
            val title = h.getString("title")
            val extract = pages.optJSONObject(id)?.optString("extract").orEmpty()
                .ifBlank { Jsoup.parse(h.optString("snippet")).text() }
            WebResult(title, "https://$lang.wikipedia.org/wiki/${enc(title.replace(' ', '_'))}", extract)
        }
    }

    private fun googleNews(q: String, limit: Int): List<WebResult> {
        val xml = Jsoup.connect("https://news.google.com/rss/search?q=${enc(q)}&hl=id&gl=ID&ceid=ID:id")
            .userAgent(UA).ignoreContentType(true).timeout(TIMEOUT).execute().body()
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.select("item").take(limit).map { item ->
            val title = item.selectFirst("title")?.text().orEmpty()
            val link = item.selectFirst("link")?.text().orEmpty()
            val date = item.selectFirst("pubDate")?.text().orEmpty()
            val desc = Jsoup.parse(item.selectFirst("description")?.text().orEmpty()).text()
            WebResult(title, link, listOf(date, desc).filter { it.isNotBlank() }.joinToString(" — ").take(400))
        }
    }

    /** Mengunduh teks bersih dari halaman web (fitur Pembaca & Perangkum URL). */
    suspend fun readArticle(url: String, maxChars: Int): WebResult = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect(url).userAgent(UA).timeout(15_000).followRedirects(true).get()
        doc.select("script, style, noscript, nav, header, footer, aside, form, iframe, svg, .ads, .advertisement, .share, .comments").remove()
        val main = doc.selectFirst("article") ?: doc.selectFirst("main") ?: doc.body()
        val paragraphs = main.select("h1, h2, h3, p, li").map { it.text().trim() }.filter { it.length > 30 }
        val text = (if (paragraphs.isNotEmpty()) paragraphs.joinToString("\n") else main.text()).take(maxChars)
        WebResult(doc.title().ifBlank { url }, url, text)
    }
}
