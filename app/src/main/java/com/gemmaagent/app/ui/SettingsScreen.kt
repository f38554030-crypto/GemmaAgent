package com.gemmaagent.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.gemmaagent.app.GemmaChatApp
import com.gemmaagent.app.data.Accent
import com.gemmaagent.app.data.CopyState
import com.gemmaagent.app.data.FontScale
import com.gemmaagent.app.data.ThemeMode
import com.gemmaagent.app.data.ModelSource
import com.gemmaagent.app.llm.Personas
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.LocalAccent
import com.gemmaagent.app.ui.theme.accentColors
import com.gemmaagent.app.util.CrashLogger
import com.gemmaagent.app.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenMemory: () -> Unit, onOpenStats: () -> Unit, onReplayOnboarding: () -> Unit, onOpenModelHub: () -> Unit, onOpenKnowledge: () -> Unit) {
    val context = LocalContext.current
    val app = GemmaChatApp.instance
    val s by app.settings.settings.collectAsState()
    val models by app.models.models.collectAsState()
    val active by app.models.active.collectAsState()
    val scanning by app.models.scanning.collectAsState()
    val copyState by app.models.copyState.collectAsState()
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf<String?>(null) }
    var crash by remember { mutableStateOf(CrashLogger.read(context)) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    var pendingBackup by remember { mutableStateOf<Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) pendingBackup = uri
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) confirmRestore = uri }

    Column(
        Modifier.fillMaxSize().background(GA.Black).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = GA.TextPrimary) }
            Text("Pengaturan", color = GA.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {

            crash?.let { text ->
                Section("Laporan error terakhir") {
                    Text(text.lines().take(8).joinToString("\n"), color = GA.Red, fontSize = 11.sp)
                    Row {
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("error", text))
                        }) { Text("Salin") }
                        TextButton(onClick = { CrashLogger.clear(context); crash = null }) { Text("Tutup") }
                    }
                }
            }

            // ---------------- MODEL ----------------
            Section("Model & Mesin") {
                Text("Aktif: ${active?.name ?: "belum ada"}", color = GA.TextPrimary, fontSize = 13.sp)
                Text("Atur model .gguf / .litertlm, parameter per model (template, temperature, repeat penalty, top-p, n_ctx, stop words), benchmark, dan unduhan Drive di Model Hub.",
                    color = GA.TextSecondary, fontSize = 12.sp)
                Button(onClick = onOpenModelHub) { Text("Buka Model Hub") }
                Label("Jendela riwayat: ${s.historyTurns} giliran terakhir", "Pesan lebih lama dipangkas agar RAM aman.")
                ChipRow(listOf(5, 6, 7, 8), s.historyTurns, { "$it" }) { v -> app.settings.update { it.copy(historyTurns = v) } }
                OutlinedButton(onClick = { app.llm.clearCache { message = "🧹 KV cache dilepas dari RAM." } }) { Text("Bersihkan Cache RAM") }
            }

            // ---------------- PERCAKAPAN (fitur 6, 12, 14, 15, 45) ----------------
            Section("Percakapan") {
                SwitchRow("Mode Berpikir (Thinking)", "Default OFF agar respons instan", s.thinking) { v -> app.settings.update { it.copy(thinking = v) } }
                SwitchRow("Saran pertanyaan lanjutan", "Menambah ±5–10 detik setelah jawaban", s.followUps) { v -> app.settings.update { it.copy(followUps = v) } }
                SwitchRow("Gunakan Memori Jangka Panjang", "Fakta tentang Anda disisipkan ke konteks", s.useMemory) { v -> app.settings.update { it.copy(useMemory = v) } }
                SwitchRow("Gunakan Basis Pengetahuan (RAG)", "Cari kutipan dokumen pribadi sebelum menjawab", s.useKnowledge) { v -> app.settings.update { it.copy(useKnowledge = v) } }
                OutlinedButton(onClick = onOpenKnowledge) { Text("Kelola Basis Pengetahuan") }
                Label("Persona", null)
                ChipRow(Personas.all.map { it.id }, s.personaId, { id -> Personas.byId(id).let { "${it.emoji} ${it.name}" } }) { v -> app.settings.update { it.copy(personaId = v) } }
                Label("System prompt kustom", "Aturan main tambahan untuk AI (opsional)")
                var custom by remember { mutableStateOf(s.customSystemPrompt) }
                OutlinedTextField(custom, { custom = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                    placeholder = { Text("mis. Selalu jawab singkat dan beri contoh.") })
                TextButton(onClick = { app.settings.update { it.copy(customSystemPrompt = custom) }; message = "System prompt disimpan." }) { Text("Simpan system prompt") }
                OutlinedButton(onClick = onOpenMemory) { Text("Buka Manajer Memori") }
            }

            // ---------------- TAMPILAN (fitur 43, 46) ----------------
            Section("Tampilan") {
                Label("Tema", "Gemini Dark (AMOLED) atau Clean Light")
                ChipRow(ThemeMode.entries.toList(), s.themeMode, { it.label }) { v -> app.settings.update { it.copy(themeMode = v) } }
                Label("Warna aksen", null)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Accent.entries.forEach { a ->
                        val c = accentColors(a)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { app.settings.update { it.copy(accent = a) } }) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(36.dp).clip(CircleShape).background(c.main)
                                    .border(if (s.accent == a) 3.dp else 0.dp, GA.Strong, CircleShape)
                            )
                            Text(a.label, color = GA.TextSecondary, fontSize = 10.sp)
                        }
                    }
                }
                Label("Ukuran huruf chat", null)
                ChipRow(FontScale.entries.toList(), s.fontScale, { it.label }) { v -> app.settings.update { it.copy(fontScale = v) } }
            }

            // ---------------- KEAMANAN (fitur 50) ----------------
            Section("Keamanan") {
                SwitchRow("Kunci aplikasi (sidik jari / kunci layar)", "Diminta setiap aplikasi dibuka", s.biometricLock) { v ->
                    app.settings.update { it.copy(biometricLock = v) }
                }
            }

            // ---------------- DATA (fitur 29, 35) ----------------
            Section("Data & Lainnya") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { backupLauncher.launch("gemmachat-backup.json") }) { Text("Backup JSON Terenkripsi") }
                    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("Restore") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenStats) { Text("Statistik") }
                    OutlinedButton(onClick = onReplayOnboarding) { Text("Ulangi Tutorial") }
                }
                Text("GemmaChat Hybrid • LiteRT-LM CPU • Semua data tersimpan lokal di HP.", color = GA.TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    message?.let { m ->
        AlertDialog(onDismissRequest = { message = null }, containerColor = GA.SurfaceHigh,
            text = { Text(m, color = GA.TextPrimary) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
    }
    pendingBackup?.let { uri ->
        PasswordDialog("Kata sandi backup", "Minimal 4 karakter. Simpan baik-baik — tanpa kata sandi ini backup tidak bisa dipulihkan.", onDismiss = { pendingBackup = null }) { pw ->
            pendingBackup = null
            scope.launch { message = runCatching { app.export.backup(uri, pw); "✅ Backup terenkripsi (AES-256) tersimpan." }.getOrElse { "Gagal backup: ${it.message}" } }
        }
    }
    confirmRestore?.let { uri ->
        PasswordDialog("Pulihkan dari backup?", "Semua riwayat, memori, catatan, dan pengaturan saat ini akan DIGANTI. Masukkan kata sandi backup.", onDismiss = { confirmRestore = null }) { pw ->
            confirmRestore = null
            scope.launch {
                message = runCatching { app.export.restore(uri, pw) }.getOrElse { "Gagal restore: ${it.message}" }
                app.chat.setSession(null)
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(GA.Surface)
            .border(1.dp, GA.Outline, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = LocalAccent.current.main, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun Label(title: String, hint: String?) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(title, color = GA.TextPrimary, fontSize = 14.sp)
        if (hint != null) Text(hint, color = GA.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val accent = LocalAccent.current
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options.size) { i ->
            val o = options[i]
            FilterChip(
                selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o), fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.deep, selectedLabelColor = androidx.compose.ui.graphics.Color.White),
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = GA.TextPrimary, fontSize = 14.sp)
            Text(hint, color = GA.TextSecondary, fontSize = 11.sp)
        }
        Switch(checked = value, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = LocalAccent.current.deep))
    }
}


@Composable
private fun PasswordDialog(title: String, body: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = GA.SurfaceHigh,
        title = { Text(title, color = GA.TextPrimary) },
        text = {
            Column {
                Text(body, color = GA.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("Kata sandi") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(enabled = pw.length >= 4, onClick = { onOk(pw) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
