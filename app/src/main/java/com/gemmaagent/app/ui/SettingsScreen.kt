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
import com.gemmaagent.app.data.ModelSource
import com.gemmaagent.app.llm.Personas
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.LocalAccent
import com.gemmaagent.app.ui.theme.accentColors
import com.gemmaagent.app.util.CrashLogger
import com.gemmaagent.app.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenMemory: () -> Unit, onOpenStats: () -> Unit, onReplayOnboarding: () -> Unit) {
    val context = LocalContext.current
    val app = GemmaChatApp.instance
    val s by app.settings.settings.collectAsState()
    val models by app.models.models.collectAsState()
    val active by app.models.active.collectAsState()
    val scanning by app.models.scanning.collectAsState()
    val copyState by app.models.copyState.collectAsState()
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()

    var storageOk by remember { mutableStateOf(app.models.hasStorageAccess()) }
    var message by remember { mutableStateOf<String?>(null) }
    var crash by remember { mutableStateOf(CrashLogger.read(context)) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        storageOk = app.models.hasStorageAccess()
        app.models.refresh()
    }

    val legacyStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        storageOk = it; app.models.refresh()
    }
    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        runCatching { if (uri != null) app.models.importModel(uri) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { app.export.backup(uri); "✅ Backup tersimpan." }.getOrElse { "Gagal backup: ${it.message}" }
        }
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

            // ---------------- MODEL (fitur 26 + zero-copy) ----------------
            Section("Model (.litertlm)") {
                if (!storageOk) {
                    Text("Izinkan \"Akses Semua File\" agar model di folder Download dimuat langsung tanpa salin (zero-copy).", color = GA.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure {
                                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        } else legacyStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }) { Text("Izinkan Akses Semua File") }
                } else {
                    Text(if (scanning) "Memindai folder Download…" else "Model terdeteksi: ${models.size}", color = GA.TextSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                models.forEach { m ->
                    val selected = m.path == active?.path
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accent.deep.copy(alpha = 0.25f) else GA.SurfaceHigh)
                            .clickable { app.models.select(m) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                            tint = if (selected) accent.main else GA.TextSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(m.name, color = GA.TextPrimary, fontSize = 13.sp)
                            Text("${Fmt.gb(m.sizeBytes)} GB • " + if (m.source == ModelSource.DOWNLOAD_DIRECT) "langsung dari Download" else "salinan internal",
                                color = GA.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { app.models.refresh() }, enabled = !scanning) { Text("Pindai Ulang") }
                    TextButton(onClick = { runCatching { pickModel.launch(arrayOf("*/*")) } }, enabled = copyState !is CopyState.Copying) { Text("Pilih manual (salin)") }
                }
                when (val c = copyState) {
                    is CopyState.Copying -> {
                        Text("Menyalin… " + c.percent + "% (" + Fmt.gb(c.copiedBytes) + " GB)", color = accent.main, fontSize = 12.sp)
                        LinearProgressIndicator(progress = { c.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    is CopyState.Done -> Text(c.message, color = GA.Green, fontSize = 12.sp)
                    is CopyState.Error -> Text(c.message, color = GA.Red, fontSize = 12.sp)
                    CopyState.Idle -> Unit
                }
                if (app.models.internalFile.exists() && models.any { it.source == ModelSource.DOWNLOAD_DIRECT }) {
                    TextButton(onClick = { app.models.deleteInternalCopy() }) { Text("Hapus salinan internal (hemat ±2,6 GB)") }
                }
            }

            // ---------------- MESIN (fitur 30, 31, 34 + spesifikasi) ----------------
            Section("Mesin AI (CPU)") {
                Label("Thread CPU: ${s.cpuThreads}", "4–6 thread. Lebih banyak belum tentu lebih cepat di Helio G96.")
                ChipRow(listOf(4, 5, 6), s.cpuThreads, { "$it" }) { v -> app.settings.update { it.copy(cpuThreads = v) } }
                Label("Batas konteks: ${s.contextTokens} token", "Total input + output. Lebih besar = ingat lebih banyak tapi butuh RAM lebih.")
                ChipRow(listOf(512, 1536, 3072), s.contextTokens, { "$it" }) { v -> app.settings.update { it.copy(contextTokens = v) } }
                Label("Panjang jawaban maks: ${s.maxOutputTokens} token", "Tetap dibatasi oleh batas konteks di atas.")
                ChipRow(listOf(512, 1536, 3072), s.maxOutputTokens, { "$it" }) { v -> app.settings.update { it.copy(maxOutputTokens = v) } }
                Label("Kreativitas (temperature): ${Fmt.one(s.temperature.toDouble())}", "0,1 = sangat faktual • 0,9 = kreatif")
                Slider(value = s.temperature, onValueChange = { v -> app.settings.update { it.copy(temperature = (Math.round(v * 10) / 10f).coerceIn(0.1f, 0.9f)) } },
                    valueRange = 0.1f..0.9f, steps = 7)
                Label("Jendela riwayat: ${s.historyTurns} giliran terakhir", "Pesan lebih lama dipangkas agar RAM aman.")
                ChipRow(listOf(5, 6, 7, 8), s.historyTurns, { "$it" }) { v -> app.settings.update { it.copy(historyTurns = v) } }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    app.llm.clearCaches { app.chat.preload() }
                    message = "🧹 Cache & memori mesin dibersihkan. Model dimuat ulang."
                }) { Text("Bersihkan Cache & Memori") }
                Text("Perubahan thread/konteks diterapkan otomatis pada pesan berikutnya (model dimuat ulang).", color = GA.TextSecondary, fontSize = 11.sp)
            }

            // ---------------- PERCAKAPAN (fitur 6, 12, 14, 15, 45) ----------------
            Section("Percakapan") {
                SwitchRow("Mode Berpikir (Thinking)", "Default OFF agar respons instan", s.thinking) { v -> app.settings.update { it.copy(thinking = v) } }
                SwitchRow("Saran pertanyaan lanjutan", "Menambah ±5–10 detik setelah jawaban", s.followUps) { v -> app.settings.update { it.copy(followUps = v) } }
                SwitchRow("Gunakan Memori Jangka Panjang", "Fakta tentang Anda disisipkan ke konteks", s.useMemory) { v -> app.settings.update { it.copy(useMemory = v) } }
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
                Label("Warna aksen", null)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Accent.entries.forEach { a ->
                        val c = accentColors(a)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { app.settings.update { it.copy(accent = a) } }) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(36.dp).clip(CircleShape).background(c.main)
                                    .border(if (s.accent == a) 3.dp else 0.dp, androidx.compose.ui.graphics.Color.White, CircleShape)
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
                    OutlinedButton(onClick = { backupLauncher.launch("gemmachat-backup.json") }) { Text("Backup JSON") }
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
    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            containerColor = GA.SurfaceHigh,
            title = { Text("Pulihkan dari backup?", color = GA.TextPrimary) },
            text = { Text("Semua riwayat, memori, catatan, dan pengaturan saat ini akan DIGANTI isi file backup.", color = GA.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    scope.launch {
                        message = runCatching { app.export.restore(uri) }.getOrElse { "Gagal restore: ${it.message}" }
                        app.chat.setSession(null)
                    }
                }) { Text("Pulihkan", color = GA.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Batal") } },
        )
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
