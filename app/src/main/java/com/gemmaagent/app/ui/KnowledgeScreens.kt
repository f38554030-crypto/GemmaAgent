package com.gemmaagent.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemmaagent.app.GemmaChatApp
import com.gemmaagent.app.data.kb.WebCacheEntity
import com.gemmaagent.app.docs.DocumentReader
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.LocalAccent
import com.gemmaagent.app.web.WebSearchEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))

@Composable
private fun Header(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = GA.TextPrimary) }
        Text(title, color = GA.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Arsip hasil pencarian web untuk dibaca saat offline (fitur 15). */
@Composable
fun WebArchiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val chat = GemmaChatApp.instance.chat
    val items by chat.webArchive.collectAsState(initial = emptyList())
    var reading by remember { mutableStateOf<WebCacheEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(GA.Black).statusBarsPadding().navigationBarsPadding()) {
        Header("Arsip Web Offline", onBack) { if (items.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Kosongkan", color = GA.Red) } }
        Text("Ringkasan artikel yang pernah dicari/dibaca tersimpan di sini dan dipakai otomatis saat internet mati.",
            color = GA.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (items.isEmpty()) item { Text("Arsip masih kosong. Nyalakan mode Online (🌐) lalu bertanya.", color = GA.TextSecondary) }
            items(items, key = { it.id }) { w ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GA.SurfaceHigh)
                        .border(1.dp, GA.Outline, RoundedCornerShape(16.dp)).clickable { reading = w }.padding(12.dp)
                ) {
                    Text(w.title, color = GA.Strong, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${WebSearchEngine.hostOf(w.url)} • ${fmt.format(Date(w.createdAt))}", color = GA.TextSecondary, fontSize = 11.sp)
                    Text(w.content.take(160), color = GA.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    reading?.let { w ->
        AlertDialog(
            onDismissRequest = { reading = null }, containerColor = GA.SurfaceHigh,
            title = { Text(w.title, color = GA.TextPrimary, maxLines = 3) },
            text = { Text(w.content, color = GA.TextPrimary, fontSize = 14.sp, modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) },
            confirmButton = {
                Row {
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(w.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Buka sumber") }
                    TextButton(onClick = { chat.deleteWebCache(w.id); reading = null }) { Text("Hapus", color = GA.Red) }
                    TextButton(onClick = { reading = null }) { Text("Tutup") }
                }
            },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false }, containerColor = GA.SurfaceHigh,
            title = { Text("Kosongkan arsip web?", color = GA.TextPrimary) },
            confirmButton = { TextButton(onClick = { chat.clearWebCache(); confirmClear = false }) { Text("Kosongkan", color = GA.Red) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
        )
    }
}

/** Basis Pengetahuan Lokal / RAG (fitur 19). */
@Composable
fun KnowledgeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = GemmaChatApp.instance
    val docs by app.chat.kbDocs.collectAsState(initial = emptyList())
    val s by app.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            msg = "Membaca dokumen…"
            runCatching { DocumentReader.read(context, uri) }
                .onSuccess { d -> if (d.text.isBlank()) msg = "Teks tidak bisa diekstrak." else { app.chat.addToKnowledge(d); msg = null } }
                .onFailure { msg = "Gagal: ${it.message}" }
        }
    }
    Column(Modifier.fillMaxSize().background(GA.Black).statusBarsPadding().navigationBarsPadding()) {
        Header("Basis Pengetahuan", onBack) {
            IconButton(onClick = { picker.launch(arrayOf("application/pdf", "text/plain", "text/markdown")) }) { Icon(Icons.Filled.Add, "Tambah dokumen", tint = LocalAccent.current.main) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Gunakan di chat", color = GA.TextPrimary, fontSize = 14.sp)
                Text("AI mencari kutipan relevan (indeks SQLite FTS) sebelum menjawab", color = GA.TextSecondary, fontSize = 11.sp)
            }
            Switch(checked = s.useKnowledge, onCheckedChange = { v -> app.settings.update { it.copy(useKnowledge = v) } })
        }
        msg?.let { Text(it, color = GA.Amber, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (docs.isEmpty()) item { Text("Belum ada dokumen. Ketuk + untuk menambahkan PDF/TXT pribadi.", color = GA.TextSecondary) }
            items(docs, key = { it.id }) { d ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GA.SurfaceHigh)
                        .border(1.dp, GA.Outline, RoundedCornerShape(16.dp)).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(d.name, color = GA.Strong, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${d.chunks} potongan • ${d.chars} karakter • ${fmt.format(Date(d.createdAt))}", color = GA.TextSecondary, fontSize = 11.sp)
                    }
                    TextButton(onClick = { app.chat.deleteKnowledgeDoc(d.id) }) { Text("Hapus", color = GA.Red) }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
