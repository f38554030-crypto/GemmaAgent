package com.gemmaagent.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.gemmaagent.app.GemmaAgentApp
import com.gemmaagent.app.data.CopyState
import com.gemmaagent.app.data.ModelRepository
import com.gemmaagent.app.data.ModelSource
import com.gemmaagent.app.util.CrashLogger
import com.gemmaagent.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.gemmaagent.app.service.AgentAccessibilityService
import com.gemmaagent.app.ui.theme.GA

@Composable
fun DashboardScreen(onStartChat: () -> Unit) {
    val context = LocalContext.current
    val app = GemmaAgentApp.instance
    val modelInfo by app.modelRepository.info.collectAsState()
    val scanning by app.modelRepository.scanning.collectAsState()
    val copyState by app.modelRepository.copyState.collectAsState()
    val modelReady = modelInfo.ready
    val scope = rememberCoroutineScope()

    var storageOk by remember { mutableStateOf(app.modelRepository.hasStorageAccess()) }
    var crashText by remember { mutableStateOf(CrashLogger.read(context)) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessOk by remember { mutableStateOf(AgentAccessibilityService.isEnabled(context)) }
    var notifOk by remember { mutableStateOf(hasNotificationPermission(context)) }

    // Cek ulang status setiap kali pengguna kembali dari Pengaturan HP.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayOk = Settings.canDrawOverlays(context)
        accessOk = AgentAccessibilityService.isEnabled(context)
        notifOk = hasNotificationPermission(context)
        storageOk = app.modelRepository.hasStorageAccess()
        app.modelRepository.refresh()
    }

    val pickModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        try {
            if (uri != null) app.modelRepository.importModel(uri)
        } catch (t: Throwable) {
            Toast.makeText(context, "Gagal membuka file: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }
    val legacyStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        storageOk = granted
        app.modelRepository.refresh()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifOk = granted
    }

    val copying = copyState is CopyState.Copying
    val allReady = modelReady && overlayOk && accessOk && !copying
    val doneCount = listOf(modelReady, overlayOk, accessOk).count { it }

    Column(
        Modifier
            .fillMaxSize()
            .background(GA.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // ---- Header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(GA.Blue, GA.VioletDeep))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Gemma Agent", color = GA.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Agen AI lokal • 100% offline • CPU", color = GA.TextSecondary, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Laporan error terakhir (jika pernah crash) ----
        crashText?.let { text ->
            CrashCard(
                text = text,
                onCopy = { copyToClipboard(context, text) },
                onDismiss = {
                    CrashLogger.clear(context)
                    crashText = null
                },
            )
            Spacer(Modifier.height(16.dp))
        }

        // ---- Progres izin ----
        val progress by animateFloatAsState(doneCount / 3f, label = "setupProgress")
        Text("Persiapan: $doneCount dari 3 izin wajib", color = GA.TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            color = if (doneCount == 3) GA.Green else GA.Blue,
            trackColor = GA.SurfaceHigher,
            drawStopIndicator = {},
        )

        Spacer(Modifier.height(18.dp))

        // (a) Model
        ChecklistCard(
            number = "1",
            icon = Icons.Filled.FolderOpen,
            title = "Pilih File Model",
            description = when {
                modelInfo.source == ModelSource.DOWNLOAD_DIRECT ->
                    "Dibaca langsung dari Download (tanpa salin):\n${modelInfo.path}\n${Fmt.gb(modelInfo.sizeBytes)} GB"
                modelInfo.source == ModelSource.INTERNAL_COPY ->
                    "Memakai salinan internal: ${modelInfo.name} (${Fmt.gb(modelInfo.sizeBytes)} GB)"
                !storageOk ->
                    "Izinkan \"Akses Semua File\" agar aplikasi otomatis membaca ${ModelRepository.PREFERRED_NAME} langsung dari folder Download, tanpa menyalin 2,59 GB."
                scanning -> "Memindai folder Download…"
                else ->
                    "File .litertlm tidak ditemukan di /storage/emulated/0/Download. Pastikan unduhan sudah selesai, lalu ketuk Pindai Ulang."
            },
            done = modelReady,
            buttonLabel = when {
                !storageOk -> "Izinkan Akses Semua File"
                else -> "Pindai Ulang Folder Download"
            },
            buttonEnabled = !copying && !scanning,
            onClick = {
                app.feedback.clickHaptic()
                if (!storageOk) {
                    requestStorageAccess(context) { legacyStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }
                } else {
                    app.modelRepository.refresh()
                }
            },
        ) {
            CopyProgress(copyState)
            if (modelInfo.source != ModelSource.DOWNLOAD_DIRECT) {
                TextButton(
                    enabled = !copying,
                    onClick = {
                        app.feedback.clickHaptic()
                        try {
                            pickModel.launch(arrayOf("*/*"))
                        } catch (t: Throwable) {
                            Toast.makeText(context, "File Picker tidak bisa dibuka: ${t.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                ) { Text("Cara cadangan: pilih file manual (disalin ke aplikasi)", fontSize = 12.sp) }
            } else if (app.modelRepository.internalFile.exists()) {
                TextButton(onClick = { app.modelRepository.deleteInternalCopy() }) {
                    Text("Hapus salinan internal lama (hemat ruang)", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // (b) Overlay
        ChecklistCard(
            number = "2",
            icon = Icons.Filled.Layers,
            title = "Izin Tampil di Atas Aplikasi Lain",
            description = "Untuk tombol melayang STOP AGEN dan pop-up konfirmasi aksi sensitif.",
            done = overlayOk,
            buttonLabel = "Buka Pengaturan Overlay",
            onClick = {
                app.feedback.clickHaptic()
                openSafely(
                    context,
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                )
            },
        )

        Spacer(Modifier.height(12.dp))

        // (c) Accessibility
        ChecklistCard(
            number = "3",
            icon = Icons.Filled.AccessibilityNew,
            title = "Aktifkan Layanan Aksesibilitas",
            description = "Cari \"Gemma Agent (Kontrol Layar AI)\" di daftar Aplikasi terunduh / Layanan terinstal, lalu aktifkan.",
            done = accessOk,
            buttonLabel = "Buka Pengaturan Aksesibilitas",
            onClick = {
                app.feedback.clickHaptic()
                openSafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        ) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    app.feedback.clickHaptic()
                    testing = true
                    scope.launch {
                        testResult = runScreenReadTest(context)
                        testing = false
                    }
                },
                enabled = !testing,
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (testing) "Membaca…" else "Tes Baca Layar", fontSize = 13.sp)
            }
            if (!accessOk) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tombol aksesibilitas abu-abu / \"Setelan terbatas\"? Buka Info Aplikasi → menu ⋮ (kanan atas) → \"Izinkan setelan terbatas\", lalu coba lagi.",
                    color = GA.Amber, fontSize = 12.sp,
                )
                TextButton(onClick = { openAppDetails(context) }) { Text("Buka Info Aplikasi") }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- Tips MIUI / opsional ----
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GA.Surface)
                .border(1.dp, GA.Outline, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Text("Disarankan untuk HP Xiaomi / POCO (MIUI/HyperOS)", color = GA.Violet, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            OptionalRow(
                icon = Icons.Filled.Notifications,
                text = if (notifOk) "Izin notifikasi aktif ✓" else "Izinkan notifikasi (agar agen terlihat di status bar)",
                action = if (notifOk || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null else "Izinkan",
                onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
            OptionalRow(
                icon = Icons.Filled.BatteryChargingFull,
                text = "Penghemat baterai → \"Tanpa batasan\" & aktifkan \"Mulai otomatis\" di Info Aplikasi",
                action = "Buka",
                onClick = { openAppDetails(context) },
            )
            OptionalRow(
                icon = Icons.Filled.Lock,
                text = "Kunci aplikasi di menu Terbaru (geser ke bawah pada kartu aplikasi) agar tidak ditutup MIUI",
                action = null,
                onClick = {},
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---- Tombol utama ----
        val btnColor by animateColorAsState(if (allReady) GA.Blue else GA.SurfaceHigher, label = "btnColor")
        Button(
            onClick = {
                app.feedback.clickHaptic()
                onStartChat()
            },
            enabled = allReady,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = btnColor,
                disabledContainerColor = GA.SurfaceHigher,
                disabledContentColor = GA.TextSecondary,
            ),
        ) {
            Text("Mulai Chat Agen", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
        if (!allReady) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (copying) "Tunggu penyalinan model selesai…" else "Tombol aktif setelah ketiga izin di atas bertanda centang hijau.",
                color = GA.TextSecondary, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    testResult?.let { result ->
        AlertDialog(
            onDismissRequest = { testResult = null },
            containerColor = GA.SurfaceHigh,
            title = { Text("Hasil Tes Baca Layar", color = GA.TextPrimary) },
            text = {
                Text(
                    result,
                    color = GA.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { testResult = null }) { Text("Tutup") } },
        )
    }
}

/** Menguji Accessibility Service: membaca teks yang sedang tampil di layar (layar Beranda ini). */
private suspend fun runScreenReadTest(context: Context): String {
    if (!AgentAccessibilityService.isEnabled(context)) {
        return "❌ Layanan Aksesibilitas BELUM aktif.\n\nKetuk \"Buka Pengaturan Aksesibilitas\" lalu aktifkan \"Gemma Agent (Kontrol Layar AI)\"."
    }
    val svc = AgentAccessibilityService.instance
        ?: return "⚠️ Layanan sudah dicentang di Pengaturan, tetapi belum terhubung ke aplikasi.\n\nMatikan lalu nyalakan lagi \"Gemma Agent (Kontrol Layar AI)\" di Pengaturan Aksesibilitas, atau mulai ulang HP."
    return try {
        val snap = withContext(Dispatchers.Default) { svc.readScreen() }
        if (snap.elements.isEmpty()) {
            "⚠️ Layanan terhubung, tetapi tidak ada teks terbaca dari layar (aplikasi: ${snap.packageName}). Coba lagi beberapa detik kemudian."
        } else {
            val lines = snap.elements.take(25).joinToString("\n") { e ->
                val tag = when {
                    e.editable -> "[kolom-ketik] "
                    e.clickable -> "[tombol] "
                    else -> ""
                }
                "• $tag${e.text}"
            }
            "✅ Berhasil! Agen bisa membaca ${snap.elements.size} teks dari layar.\nAplikasi aktif: ${snap.packageName}\n\n$lines"
        }
    } catch (t: Throwable) {
        "❌ Gagal membaca layar: ${t.message ?: t.javaClass.simpleName}"
    }
}

@Composable
private fun CrashCard(text: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GA.Red.copy(alpha = 0.08f))
            .border(1.dp, GA.Red.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text("Laporan error terakhir", color = GA.Red, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Screenshot atau salin teks ini untuk perbaikan:",
            color = GA.TextSecondary, fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text.lines().take(8).joinToString("\n"),
            color = GA.TextPrimary, fontSize = 11.sp,
            modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
        )
        Row {
            TextButton(onClick = onCopy) { Text("Salin") }
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Gemma Agent error", text))
        Toast.makeText(context, "Laporan error disalin", Toast.LENGTH_SHORT).show()
    }
}

/** Membuka halaman izin "Akses Semua File" (Android 11+) atau meminta izin baca penyimpanan (Android 10-). */
private fun requestStorageAccess(context: Context, legacyRequest: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            openSafely(context, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    } else {
        legacyRequest()
    }
}

@Composable
private fun ChecklistCard(
    number: String,
    icon: ImageVector,
    title: String,
    description: String,
    done: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
    buttonEnabled: Boolean = true,
    extra: @Composable () -> Unit = {},
) {
    val borderColor by animateColorAsState(if (done) GA.Green.copy(alpha = 0.55f) else GA.Outline, label = "border")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GA.Surface)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(GA.SurfaceHigher),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = if (done) GA.Green else GA.Blue) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Langkah $number", color = GA.TextSecondary, fontSize = 11.sp)
                Text(title, color = GA.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (done) "Selesai" else "Belum",
                tint = if (done) GA.Green else GA.TextSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(description, color = GA.TextSecondary, fontSize = 13.sp)
        extra()
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(
            onClick = onClick,
            enabled = buttonEnabled,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (done) GA.SurfaceHigher else GA.BlueDeep,
                contentColor = Color.White,
            ),
        ) { Text(buttonLabel) }
    }
}

@Composable
private fun CopyProgress(state: CopyState) {
    when (state) {
        is CopyState.Copying -> {
            Spacer(Modifier.height(10.dp))
            val text = if (state.totalBytes > 0) {
                "Menyalin model… " + state.percent + "% (" + Fmt.gb(state.copiedBytes) + " / " + Fmt.gb(state.totalBytes) + " GB)"
            } else {
                "Menyalin model… " + Fmt.gb(state.copiedBytes) + " GB"
            }
            Text(text, color = GA.Blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = GA.Blue,
                trackColor = GA.SurfaceHigher,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(4.dp))
            Text("Jangan tutup aplikasi sampai selesai.", color = GA.TextSecondary, fontSize = 11.sp)
        }
        is CopyState.Done -> {
            Spacer(Modifier.height(8.dp))
            Text(state.message, color = GA.Green, fontSize = 12.sp)
        }
        is CopyState.Error -> {
            Spacer(Modifier.height(8.dp))
            Text(state.message, color = GA.Red, fontSize = 12.sp)
        }
        CopyState.Idle -> Unit
    }
}

@Composable
private fun OptionalRow(icon: ImageVector, text: String, action: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = GA.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = GA.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        AnimatedVisibility(visible = action != null) {
            TextButton(onClick = onClick) { Text(action ?: "") }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun openAppDetails(context: Context) {
    openSafely(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

private fun openSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            Toast.makeText(context, "Tidak bisa membuka Pengaturan", Toast.LENGTH_SHORT).show()
        }
    }
}
