package com.gemmaagent.app.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gemmaagent.app.ui.theme.GA
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Studio Kode: pratinjau HTML/CSS/JS/SVG/Mermaid di WebView lokal (tanpa internet) dan JSON rapi. */
@Composable
fun CodePreviewDialog(lang: String, code: String, onDismiss: () -> Unit) {
    val kind = CodePreview.kind(lang, code) ?: "html"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GA.SurfaceHigh,
        title = { Text("Pratinjau ${kind.uppercase()}", color = GA.TextPrimary) },
        text = {
            if (kind == "json") {
                val pretty = remember(code) { prettyJson(code) }
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(RoundedCornerShape(12.dp)).background(GA.CodeBg)
                        .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(10.dp)
                ) {
                    Text(pretty, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = GA.TextPrimary, softWrap = false)
                }
            } else {
                LocalWebView(buildHtml(kind, code))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LocalWebView(html: String) {
    var view by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { view?.destroy() } }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.blockNetworkLoads = true      // pratinjau sepenuhnya offline
                settings.allowFileAccess = true
                settings.domStorageEnabled = false
                webViewClient = WebViewClient()
                loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
                view = this
            }
        },
        modifier = Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(12.dp)),
    )
}

private fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun buildHtml(kind: String, code: String): String = when (kind) {
    "css" -> """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>$code</style></head>
        <body><h1>Judul</h1><p>Paragraf contoh dengan <a href="#">tautan</a>.</p><button>Tombol</button>
        <div class="container box card">Kotak .container .box .card</div><ul><li>Satu</li><li>Dua</li></ul></body></html>"""
    "js" -> """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>body{font-family:monospace;background:#0e0e10;color:#e3e3e8;padding:8px}#out div{border-bottom:1px solid #333;padding:4px 0}.err{color:#ea4335}</style></head>
        <body><div id="out"></div><script>
        (function(){var o=document.getElementById('out');function w(c,a){var d=document.createElement('div');if(c)d.className=c;
        d.textContent=Array.prototype.map.call(a,function(x){try{return typeof x==='object'?JSON.stringify(x):String(x)}catch(e){return String(x)}}).join(' ');o.appendChild(d)}
        console.log=function(){w('',arguments)};console.error=function(){w('err',arguments)};
        window.onerror=function(m){w('err',['Error: '+m])};
        try{${code.replace("</script>", "<\\/script>")}
        }catch(e){w('err',['Error: '+e.message])}
        if(!o.childNodes.length)w('',['(selesai, tidak ada output console.log)']);})();
        </script></body></html>"""
    "mermaid" -> """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>body{background:#ffffff;margin:8px}</style><script src="mermaid.min.js"></script></head>
        <body><pre class="mermaid">${escapeHtml(code)}</pre>
        <script>try{mermaid.initialize({startOnLoad:true,securityLevel:'strict'});}catch(e){document.body.innerHTML='Mermaid gagal: '+e.message}</script></body></html>"""
    else -> if (code.contains("<html", true)) code
    else """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>$code</body></html>"""
}

private fun prettyJson(code: String): String = runCatching {
    when (val v = JSONTokener(code.trim()).nextValue()) {
        is JSONObject -> v.toString(2)
        is JSONArray -> v.toString(2)
        else -> v.toString()
    }
}.getOrElse { "JSON tidak valid: ${it.message}\n\n$code" }

// ============================== DIFF VIEWER ==============================

enum class DiffOp { SAME, ADD, DEL }

object TextDiff {
    /** Diff per kata berbasis LCS (dibatasi agar hemat RAM). */
    fun words(a: String, b: String, max: Int = 1500): List<Pair<DiffOp, String>> {
        val x = Regex("\\S+\\s*").findAll(a).map { it.value }.take(max).toList()
        val y = Regex("\\S+\\s*").findAll(b).map { it.value }.take(max).toList()
        val n = x.size
        val m = y.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            dp[i][j] = if (x[i].trim() == y[j].trim()) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = mutableListOf<Pair<DiffOp, String>>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                x[i].trim() == y[j].trim() -> { out += DiffOp.SAME to y[j]; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { out += DiffOp.DEL to x[i]; i++ }
                else -> { out += DiffOp.ADD to y[j]; j++ }
            }
        }
        while (i < n) out += DiffOp.DEL to x[i++]
        while (j < m) out += DiffOp.ADD to y[j++]
        return out
    }

    fun annotated(ops: List<Pair<DiffOp, String>>): AnnotatedString = buildAnnotatedString {
        ops.forEach { (op, w) ->
            when (op) {
                DiffOp.SAME -> append(w)
                DiffOp.ADD -> withStyle(SpanStyle(background = GA.Green.copy(alpha = 0.25f), color = GA.Strong)) { append(w) }
                DiffOp.DEL -> withStyle(SpanStyle(background = GA.Red.copy(alpha = 0.2f), textDecoration = TextDecoration.LineThrough, color = GA.TextSecondary)) { append(w) }
            }
        }
    }
}

/** Pembanding revisi teks: dua versi jawaban, atau dua teks bebas. */
@Composable
fun DiffDialog(initialA: String, initialB: String, editable: Boolean, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf(initialA) }
    var b by remember { mutableStateOf(initialB) }
    val ops = remember(a, b) { TextDiff.words(a, b) }
    val added = ops.count { it.first == DiffOp.ADD }
    val removed = ops.count { it.first == DiffOp.DEL }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GA.SurfaceHigh,
        title = { Text("Diff Viewer", color = GA.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (editable) {
                    OutlinedTextField(a, { a = it }, label = { Text("Teks lama") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(b, { b = it }, label = { Text("Teks baru") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp))
                    Spacer(Modifier.height(8.dp))
                }
                Row {
                    Text("+$added kata", color = GA.Green, fontSize = 12.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("−$removed kata", color = GA.Red, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    TextDiff.annotated(ops), color = GA.TextPrimary, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GA.CodeBg)
                        .border(1.dp, GA.Outline, RoundedCornerShape(12.dp)).padding(10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
