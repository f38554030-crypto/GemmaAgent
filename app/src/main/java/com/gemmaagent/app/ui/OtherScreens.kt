package com.gemmaagent.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemmaagent.app.GemmaChatApp
import com.gemmaagent.app.chat.UsageStats
import com.gemmaagent.app.data.db.MemoryEntity
import com.gemmaagent.app.data.db.NoteEntity
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.LocalAccent
import com.gemmaagent.app.util.Fmt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = GA.TextPrimary) }
        Text(title, color = GA.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

private val dateFmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID"))

// ============================== MANAJER MEMORI (fitur 13) ==============================

@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val chat = GemmaChatApp.instance.chat
    val memories by chat.memories.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(GA.Black).statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader("Memori AI", onBack) {
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Tambah fakta", tint = LocalAccent.current.main) }
        }
        Text(
            "Fakta yang diingat AI lintas sesi. Otomatis tersimpan saat Anda menulis seperti \"nama saya…\", \"saya bekerja sebagai…\", atau \"ingat bahwa…\".",
            color = GA.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (memories.isEmpty()) item { Text("Belum ada memori.", color = GA.TextSecondary) }
            items(memories, key = { it.id }) { m ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GA.Surface)
                        .border(1.dp, GA.Outline, RoundedCornerShape(14.dp)).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Psychology, null, tint = GA.Violet, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(m.fact, color = GA.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                    IconButton(onClick = { editing = m }) { Icon(Icons.Filled.Edit, "Edit", tint = GA.TextSecondary) }
                    IconButton(onClick = { chat.deleteMemory(m.id) }) { Icon(Icons.Filled.Delete, "Hapus", tint = GA.Red) }
                }
            }
        }
        if (memories.isNotEmpty()) {
            TextButton(onClick = { confirmClear = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Hapus semua memori", color = GA.Red) }
        }
    }

    if (adding || editing != null) {
        var text by remember(editing) { mutableStateOf(editing?.fact.orEmpty()) }
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            containerColor = GA.SurfaceHigh,
            title = { Text(if (editing != null) "Edit fakta" else "Tambah fakta", color = GA.TextPrimary) },
            text = { OutlinedTextField(text, { text = it }, placeholder = { Text("mis. Saya guru matematika SMA") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    val e = editing
                    if (e != null) chat.updateMemory(e.copy(fact = text.trim())) else chat.addMemory(text)
                    adding = false; editing = null
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text("Batal") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false }, containerColor = GA.SurfaceHigh,
            title = { Text("Hapus semua memori?", color = GA.TextPrimary) },
            confirmButton = { TextButton(onClick = { chat.clearMemories(); confirmClear = false }) { Text("Hapus", color = GA.Red) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
        )
    }
}

// ============================== CATATAN & FAVORIT (fitur 22, 25) ==============================

@Composable
fun NotesScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit) {
    val context = LocalContext.current
    val chat = GemmaChatApp.instance.chat
    val notes by chat.notes.collectAsState(initial = emptyList())
    val starred by chat.starred.collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<NoteEntity?>(null) }
    fun copy(t: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("catatan", t))
    }

    Column(Modifier.fillMaxSize().background(GA.Black).statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader("Catatan & Favorit", onBack)
        TabRow(selectedTabIndex = tab, containerColor = GA.Black, contentColor = LocalAccent.current.main) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Catatan (${notes.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Berbintang (${starred.size})") })
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == 0) {
                if (notes.isEmpty()) item { Text("Belum ada catatan. Ketuk ikon bookmark di bawah jawaban AI untuk menyimpan.", color = GA.TextSecondary) }
                items(notes, key = { "n${it.id}" }) { n ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GA.Surface)
                            .border(1.dp, GA.Outline, RoundedCornerShape(16.dp)).padding(12.dp)
                    ) {
                        Text(n.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(dateFmt.format(Date(n.createdAt)), color = GA.TextSecondary, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.heightIn(max = 220.dp)) { MarkdownText(n.text.take(1500)) }
                        Row {
                            TextButton(onClick = { copy(n.text) }) { Text("Salin") }
                            TextButton(onClick = { editing = n }) { Text("Edit") }
                            TextButton(onClick = { chat.deleteNote(n.id) }) { Text("Hapus", color = GA.Red) }
                        }
                    }
                }
            } else {
                if (starred.isEmpty()) item { Text("Belum ada pesan berbintang. Ketuk ikon bintang di bawah jawaban AI.", color = GA.TextSecondary) }
                items(starred, key = { "s${it.id}" }) { m ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GA.Surface)
                            .border(1.dp, GA.Amber.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, null, tint = GA.Amber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(dateFmt.format(Date(m.createdAt)), color = GA.TextSecondary, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.heightIn(max = 220.dp)) { MarkdownText(m.text.take(1500)) }
                        Row {
                            TextButton(onClick = { onOpenSession(m.sessionId) }) { Text("Buka percakapan") }
                            TextButton(onClick = { copy(m.text) }) { Text("Salin") }
                            TextButton(onClick = { chat.toggleStar(m) }) { Text("Hapus bintang") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { n ->
        var title by remember(n.id) { mutableStateOf(n.title) }
        var text by remember(n.id) { mutableStateOf(n.text) }
        AlertDialog(
            onDismissRequest = { editing = null }, containerColor = GA.SurfaceHigh,
            title = { Text("Edit catatan", color = GA.TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Judul") })
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(text, { text = it }, modifier = Modifier.heightIn(min = 150.dp, max = 320.dp))
                }
            },
            confirmButton = { TextButton(onClick = { chat.updateNote(n.copy(title = title, text = text)); editing = null }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Batal") } },
        )
    }
}

// ============================== STATISTIK (fitur 35) ==============================

@Composable
fun StatsScreen(onBack: () -> Unit) {
    var stats by remember { mutableStateOf<UsageStats?>(null) }
    LaunchedEffect(Unit) { stats = GemmaChatApp.instance.chat.stats() }
    val accent = LocalAccent.current
    Column(Modifier.fillMaxSize().background(GA.Black).statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader("Statistik Penggunaan", onBack)
        val st = stats
        if (st == null) {
            Text("Menghitung…", color = GA.TextSecondary, modifier = Modifier.padding(16.dp))
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Pesan terkirim", "${st.userMessages}", Modifier.weight(1f))
                StatTile("Balasan AI", "${st.aiMessages}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Rata-rata kecepatan", "${Fmt.one(st.avgTokensPerSec)} tok/dtk", Modifier.weight(1f))
                StatTile("Total token", "${st.totalTokens}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Sesi", "${st.sessions}", Modifier.weight(1f))
                StatTile("Catatan / Memori", "${st.notes} / ${st.memories}", Modifier.weight(1f))
            }
            val total = st.aiMessages.coerceAtLeast(1)
            val onlineRatio = st.onlineAnswers.toFloat() / total
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GA.Surface)
                    .border(1.dp, GA.Outline, RoundedCornerShape(18.dp)).padding(14.dp)
            ) {
                Text("Rasio Offline vs Online", color = GA.TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50))) {
                    if (onlineRatio < 1f) Box(Modifier.weight((1f - onlineRatio).coerceAtLeast(0.001f)).fillMaxSize().background(GA.Green))
                    if (onlineRatio > 0f) Box(Modifier.weight(onlineRatio.coerceAtLeast(0.001f)).fillMaxSize().background(accent.main))
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("● Offline ${st.aiMessages - st.onlineAnswers}", color = GA.Green, fontSize = 12.sp)
                    Spacer(Modifier.width(14.dp))
                    Text("● Online ${st.onlineAnswers}", color = accent.main, fontSize = 12.sp)
                }
            }
            Text("Kecepatan token/detik dihitung dari jumlah potongan teks yang dialirkan model per detik (perkiraan).", color = GA.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(GA.Surface).border(1.dp, GA.Outline, RoundedCornerShape(18.dp)).padding(14.dp)
    ) {
        Text(value, color = LocalAccent.current.main, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = GA.TextSecondary, fontSize = 12.sp)
    }
}

// ============================== ONBOARDING (fitur 48) ==============================

private data class OnboardPage(val icon: ImageVector, val title: String, val body: String)

@Composable
fun OnboardingScreen(onDone: () -> Unit, onOpenSettings: () -> Unit) {
    val pages = listOf(
        OnboardPage(Icons.Filled.FolderOpen, "1. Pilih Model",
            "Unduh gemma-4-E2B-it.litertlm ke folder Download. Di Pengaturan → Model, izinkan \"Akses Semua File\" agar model dimuat langsung tanpa disalin."),
        OnboardPage(Icons.Filled.Public, "2. Offline / Online",
            "Ikon bola dunia di samping kolom ketik menyalakan pencarian web (Web, Wikipedia, Berita). Saat mati, semua diproses 100% offline di HP."),
        OnboardPage(Icons.Filled.Psychology, "3. Mode Berpikir",
            "Ikon otak di bilah atas menyalakan penalaran bertahap untuk analisis mendalam. Default OFF agar jawaban instan."),
    )
    var page by remember { mutableIntStateOf(0) }
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxSize().background(GA.Black).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Lewati", color = GA.TextSecondary) }
        }
        Spacer(Modifier.weight(1f))
        AnimatedContent(targetState = page, label = "onboard") { p ->
            val pg = pages[p]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(accent.main, GA.VioletDeep))),
                    contentAlignment = Alignment.Center,
                ) { Icon(pg.icon, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                Spacer(Modifier.height(24.dp))
                Text(pg.title, color = GA.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(pg.body, color = GA.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.indices.forEach { i ->
                Box(Modifier.size(if (i == page) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i == page) accent.main else GA.Outline))
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { if (page < pages.lastIndex) page++ else onDone() },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent.main),
        ) { Text(if (page < pages.lastIndex) "Lanjut" else "Mulai", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        if (page == 0) {
            TextButton(onClick = onOpenSettings) { Text("Buka Pengaturan Model sekarang") }
        }
    }
}

