package com.gemmaagent.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.gemmaagent.app.GemmaChatApp
import com.gemmaagent.app.data.CopyState
import com.gemmaagent.app.data.GgufReader
import com.gemmaagent.app.data.ModelFile
import com.gemmaagent.app.data.ModelRepository
import com.gemmaagent.app.data.ModelSource
import com.gemmaagent.app.llm.ChatTemplate
import com.gemmaagent.app.llm.EngineType
import com.gemmaagent.app.llm.ModelSettings
import com.gemmaagent.app.llm.ModelStatus
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.LocalAccent
import com.gemmaagent.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Model Hub ala PocketPal AI (fitur 1–8). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = GemmaChatApp.instance
    val repo = app.models
    val models by repo.models.collectAsState()
    val active by repo.active.collectAsState()
    val scanning by repo.scanning.collectAsState()
    val copy by repo.copyState.collectAsState()
    val loaded by app.llm.loaded.collectAsState()
    val status by app.llm.status.collectAsState()
    val error by app.llm.error.collectAsState()
    val progress by app.llm.progress.collectAsState()
    val s by app.settings.settings.collectAsState()
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()

    var storageOk by remember { mutableStateOf(repo.hasStorageAccess()) }
    var editing by remember { mutableStateOf<ModelFile?>(null) }
    var info by remember { mutableStateOf<Pair<ModelFile, String>?>(null) }
    var benchText by remember { mutableStateOf<String?>(null) }
    var driveDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        storageOk = repo.hasStorageAccess()
        repo.refresh()
    }
    val legacyStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { storageOk = it; repo.refresh() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) repo.importFromUri(uri) }

    fun openUrl(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(Modifier.fillMaxSize().background(GA.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = GA.TextPrimary) }
            Text("Model Hub", color = GA.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { repo.refresh() }, enabled = !scanning) { Icon(Icons.Filled.Refresh, "Pindai ulang", tint = accent.main) }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Section("Sumber model") {
                    if (!storageOk) {
                        Text("Izinkan \"Akses Semua File\" agar file .gguf & .litertlm di folder Download terdeteksi dan dibaca langsung tanpa salin.",
                            color = GA.TextSecondary, fontSize = 12.sp)
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                            } else legacyStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }) { Text("Izinkan Akses Semua File") }
                    } else {
                        Text(if (scanning) "Memindai Download…" else "✅ Akses penyimpanan aktif • ${models.size} model terdeteksi", color = GA.Green, fontSize = 12.sp)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { runCatching { picker.launch(arrayOf("*/*")) } }) { Text("Pilih File (File Manager / ZArchiver / Drive)") }
                        OutlinedButton(onClick = { openUrl(ModelRepository.USER_DRIVE_FOLDER) }) { Text("Buka Folder Drive Saya") }
                        OutlinedButton(onClick = { driveDialog = true }) { Text("Unduh dari Drive") }
                    }
                    CopyProgressView(copy) { repo.cancelCopy() }
                }
            }

            if (status == ModelStatus.ERROR && error != null) item {
                Text(error ?: "", color = GA.Red, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GA.Red.copy(alpha = 0.1f)).padding(10.dp))
            }
            progress?.let { p ->
                item {
                    Column {
                        Text("Memuat model… ${(p * 100).toInt()}%", color = accent.main, fontSize = 12.sp)
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = accent.main, trackColor = GA.SurfaceHigher, drawStopIndicator = {})
                    }
                }
            }

            if (models.isEmpty() && !scanning) item {
                Text("Belum ada model. Letakkan file .gguf atau .litertlm di folder Download, lalu ketuk Pindai Ulang — atau unduh dari folder Drive Anda.",
                    color = GA.TextSecondary, fontSize = 13.sp)
            }

            items(models, key = { it.path }) { m ->
                ModelCard(
                    model = m,
                    selected = m.path == active?.path,
                    isLoaded = m.path == loaded?.path,
                    busy = status == ModelStatus.LOADING || status == ModelStatus.UNLOADING || status == ModelStatus.BUSY,
                    onUse = {
                        repo.select(m)
                        app.llm.loadAsync(m, app.modelSettings.get(m.path, m.type), s.cpuThreads)
                    },
                    onUnload = { app.llm.unloadAsync() },
                    onSettings = { editing = m },
                    onInfo = {
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { buildInfo(m) }
                            val runtime = if (m.path == loaded?.path && m.type == EngineType.GGUF) runCatching { app.llm.runtimeInfo() }.getOrNull() else null
                            info = m to (text + (runtime?.let { r ->
                                val parts = r.split("\n", limit = 5)
                                "\n\n— Runtime llama.cpp —\nDeskripsi: ${parts.getOrNull(0)}\nUkuran tensor: ${parts.getOrNull(1)?.toLongOrNull()?.let { Fmt.gb(it) + " GB" }}\n" +
                                    "Parameter: ${parts.getOrNull(2)?.toLongOrNull()?.let { Fmt.one(it / 1e9) + " B" }}\nKonteks latih: ${parts.getOrNull(3)}"
                            } ?: ""))
                        }
                    },
                    onBench = {
                        benchText = "Mengukur kecepatan ${m.name}… (bisa 10–60 detik)"
                        scope.launch {
                            benchText = runCatching {
                                val r = app.llm.bench(m, app.modelSettings.get(m.path, m.type), s.cpuThreads)
                                (if (r.ppTokensPerSec > 0) "📥 Baca prompt (pp128): ${Fmt.one(r.ppTokensPerSec)} tok/s\n" else "") +
                                    "✍️ Ketik (tg): ${Fmt.one(r.tgTokensPerSec)} tok/s\n\nCPU ${s.cpuThreads} thread • ${m.type.badge}"
                            }.getOrElse { "Benchmark gagal: ${it.message}" }
                        }
                    },
                    onDeleteLegacy = { repo.deleteLegacyCopy() },
                )
            }

            item {
                Section("Mesin CPU") {
                    Text("Thread CPU: ${s.cpuThreads}", color = GA.TextPrimary, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(4, 5, 6).forEach { t ->
                            FilterChip(selected = s.cpuThreads == t, onClick = { app.settings.update { it.copy(cpuThreads = t) } }, label = { Text("$t") })
                        }
                    }
                    Text("Ganti model memakai protokol Strict Unload-Before-Load: model lama dilepas dari RAM dulu, baru model baru dimuat.",
                        color = GA.TextSecondary, fontSize = 11.sp)
                    OutlinedButton(onClick = { app.llm.clearCache() }) { Text("Bersihkan Cache RAM (KV cache)") }
                }
            }
        }
    }

    editing?.let { m ->
        ModelSettingsDialog(m, app.modelSettings.get(m.path, m.type), onDismiss = { editing = null }) { ns ->
            app.modelSettings.put(m.path, ns)
            editing = null
            if (loaded?.path == m.path) app.llm.loadAsync(m, ns, s.cpuThreads) // n_ctx berubah → muat ulang aman
        }
    }
    info?.let { (m, text) ->
        AlertDialog(
            onDismissRequest = { info = null }, containerColor = GA.SurfaceHigh,
            title = { Text("Inspektor Metadata", color = GA.TextPrimary) },
            text = { Text(text, color = GA.TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("Tutup") } },
        )
    }
    benchText?.let { t ->
        AlertDialog(
            onDismissRequest = { benchText = null }, containerColor = GA.SurfaceHigh,
            title = { Text("Benchmark Kecepatan Mini", color = GA.TextPrimary) },
            text = { Text(t, color = GA.TextPrimary) },
            confirmButton = { TextButton(onClick = { benchText = null }) { Text("Tutup") } },
        )
    }
    if (driveDialog) {
        var link by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { driveDialog = false }, containerColor = GA.SurfaceHigh,
            title = { Text("Unduh dari Google Drive", color = GA.TextPrimary) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Model di folder Drive Anda (model_hp/gguf):", color = GA.TextSecondary, fontSize = 12.sp)
                    Button(
                        onClick = { driveDialog = false; repo.downloadFromDrive(ModelRepository.USER_DRIVE_GGUF_ID, ModelRepository.USER_DRIVE_GGUF_NAME) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent.deep),
                    ) { Text("${ModelRepository.USER_DRIVE_GGUF_NAME} (3,43 GB)") }
                    Spacer(Modifier.height(10.dp))
                    Text("Atau tempel link FILE Drive lain (bukan link folder). File harus dibagikan \"Siapa saja yang memiliki link\".", color = GA.TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(link, { link = it }, label = { Text("Link file Drive") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it }, label = { Text("Nama file (mis. model.gguf)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = link.isNotBlank(), onClick = { driveDialog = false; repo.downloadFromDrive(link, name) }) { Text("Unduh") }
            },
            dismissButton = { TextButton(onClick = { driveDialog = false }) { Text("Batal") } },
        )
    }
}

private fun buildInfo(m: ModelFile): String {
    val base = "File: ${m.name}\nUkuran: ${Fmt.gb(m.sizeBytes)} GB\nMesin: ${m.type.badge}\nLokasi: ${m.path}\nSumber: ${m.source.label}"
    if (m.type != EngineType.GGUF) return "$base\n\n(.litertlm menyimpan template & tokenizer di dalam paket LiteRT-LM.)"
    return runCatching {
        val md = GgufReader.read(File(m.path))
        buildString {
            append(base).append("\n\n— Header GGUF —\n")
            append("Versi GGUF: ${md.ggufVersion}\n")
            append("Arsitektur: ${md.architecture ?: "-"}\n")
            append("Nama: ${md.name ?: "-"}\n")
            append("Kuantisasi: ${md.quantization ?: "-"}\n")
            append("Konteks maks: ${md.contextLength ?: "-"}\n")
            append("Jumlah blok: ${md.blockCount ?: "-"}\n")
            append("Dimensi embedding: ${md.embeddingLength ?: "-"}\n")
            append("Jumlah tensor: ${md.tensorCount ?: "-"}\n")
            md.extra.forEach { (k, v) -> append("$k: $v\n") }
            append("\nChat template bawaan:\n")
            append(md.chatTemplate?.take(1500)?.let { if (md.chatTemplate.length > 1500) "$it…" else it } ?: "(tidak ada — pilih template manual di Pengaturan Model)")
        }
    }.getOrElse { "$base\n\nGagal membaca header GGUF: ${it.message}" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(
    model: ModelFile,
    selected: Boolean,
    isLoaded: Boolean,
    busy: Boolean,
    onUse: () -> Unit,
    onUnload: () -> Unit,
    onSettings: () -> Unit,
    onInfo: () -> Unit,
    onBench: () -> Unit,
    onDeleteLegacy: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GA.SurfaceHigh)
            .border(if (isLoaded) 2.dp else 1.dp, if (isLoaded) GA.Green else if (selected) accent.main.copy(alpha = 0.6f) else GA.Outline, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoBadge("[${model.type.badge}]", if (model.type == EngineType.GGUF) GA.Violet else accent.main)
            Spacer(Modifier.width(6.dp))
            if (isLoaded) InfoBadge("● Aktif di RAM", GA.Green) else if (selected) InfoBadge("Dipilih", accent.main)
            Spacer(Modifier.weight(1f))
            Text("${Fmt.gb(model.sizeBytes)} GB", color = GA.TextSecondary, fontSize = 12.sp)
        }
        Text(model.name, color = GA.Strong, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(model.source.label, color = GA.TextSecondary, fontSize = 11.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onUse, enabled = !busy && !isLoaded, colors = ButtonDefaults.buttonColors(containerColor = accent.main)) {
                Text(if (isLoaded) "Sedang dipakai" else "Muat / Pakai")
            }
            OutlinedButton(onClick = onUnload, enabled = isLoaded && !busy) { Text("Lepas dari Memori") }
            TextButton(onClick = onSettings) { Text("Pengaturan Model") }
            TextButton(onClick = onInfo) { Text("Info") }
            TextButton(onClick = onBench, enabled = !busy) { Text("Benchmark") }
            if (model.source == ModelSource.INTERNAL_COPY) TextButton(onClick = onDeleteLegacy) { Text("Hapus salinan", color = GA.Red) }
        }
    }
}

@Composable
private fun CopyProgressView(state: CopyState, onCancel: () -> Unit) {
    when (state) {
        is CopyState.Copying -> Column {
            Text("${state.label}… ${state.percent}%" + if (state.totalBytes > 0) " (${Fmt.gb(state.copiedBytes)} / ${Fmt.gb(state.totalBytes)} GB)" else "",
                color = LocalAccent.current.main, fontSize = 12.sp)
            LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth(), trackColor = GA.SurfaceHigher, drawStopIndicator = {})
            if (state.label.contains("cloud")) TextButton(onClick = onCancel) { Text("Batalkan") }
        }
        is CopyState.Done -> Text(state.message, color = GA.Green, fontSize = 12.sp)
        is CopyState.Error -> Text(state.message, color = GA.Red, fontSize = 12.sp)
        CopyState.Idle -> Unit
    }
}

/** Pengaturan parameter per model (fitur 3). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelSettingsDialog(model: ModelFile, initial: ModelSettings, onDismiss: () -> Unit, onSave: (ModelSettings) -> Unit) {
    var st by remember { mutableStateOf(initial) }
    var stops by remember { mutableStateOf(initial.stopWords.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = GA.SurfaceHigh,
        title = { Text("Pengaturan: ${model.shortLabel}", color = GA.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (model.type == EngineType.GGUF) {
                    Text("Chat Template", color = GA.TextPrimary, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChatTemplate.entries.forEach { t -> FilterChip(selected = st.template == t, onClick = { st = st.copy(template = t) }, label = { Text(t.label, fontSize = 11.sp) }) }
                    }
                } else {
                    Text("Chat template: bawaan file .litertlm", color = GA.TextSecondary, fontSize = 12.sp)
                }
                Text("Temperature: ${Fmt.one(st.temperature.toDouble())}", color = GA.TextPrimary, fontSize = 13.sp)
                Slider(st.temperature, { st = st.copy(temperature = (Math.round(it * 20) / 20f)) }, valueRange = 0.1f..1.5f)
                Text("Repeat Penalty: ${String.format(java.util.Locale.US, "%.2f", st.repeatPenalty)}", color = GA.TextPrimary, fontSize = 13.sp)
                Slider(st.repeatPenalty, { st = st.copy(repeatPenalty = (Math.round(it * 100) / 100f)) }, valueRange = 1.0f..1.5f)
                Text("Top-P: ${String.format(java.util.Locale.US, "%.2f", st.topP)}", color = GA.TextPrimary, fontSize = 13.sp)
                Slider(st.topP, { st = st.copy(topP = (Math.round(it * 100) / 100f)) }, valueRange = 0.1f..1.0f)
                Text("Context Window (n_ctx): ${st.nCtx}", color = GA.TextPrimary, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(512, 1024, 1536, 2048, 3072, 4096).forEach { n -> FilterChip(selected = st.nCtx == n, onClick = { st = st.copy(nCtx = n) }, label = { Text("$n") }) }
                }
                Text("Panjang jawaban maks: ${st.maxTokens} token", color = GA.TextPrimary, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(256, 512, 768, 1536, 3072).forEach { n -> FilterChip(selected = st.maxTokens == n, onClick = { st = st.copy(maxTokens = n) }, label = { Text("$n") }) }
                }
                OutlinedTextField(stops, { stops = it }, label = { Text("Stop Words (pisahkan koma)") }, modifier = Modifier.fillMaxWidth())
                Text("n_ctx besar butuh RAM lebih. Untuk RAM 8 GB disarankan ≤ 4096.", color = GA.TextSecondary, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(st.copy(stopWords = stops.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
            }) { Text("Simpan") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { st = ModelSettings.defaultsFor(model.type); stops = "" }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )
}
