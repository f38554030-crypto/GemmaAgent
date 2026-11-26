package com.gemmaagent.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemmaagent.app.GemmaChatApp
import com.gemmaagent.app.data.ModelFile
import com.gemmaagent.app.data.RamEstimate
import com.gemmaagent.app.data.RamEstimator
import com.gemmaagent.app.llm.EngineType
import com.gemmaagent.app.llm.ModelStatus
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.LocalAccent
import com.gemmaagent.app.util.DeviceInfo
import com.gemmaagent.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Pemilih model modern (ModalBottomSheet) + meteran RAM visual per model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(onDismiss: () -> Unit, onOpenHub: () -> Unit) {
    val context = LocalContext.current
    val app = GemmaChatApp.instance
    val models by app.models.models.collectAsState()
    val active by app.models.active.collectAsState()
    val loaded by app.llm.loaded.collectAsState()
    val status by app.llm.status.collectAsState()
    val progress by app.llm.progress.collectAsState()
    val s by app.settings.settings.collectAsState()
    val accent = LocalAccent.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // RAM HP real-time (diperbarui tiap 2 detik selama sheet terbuka).
    var availMb by remember { mutableLongStateOf(0L) }
    var totalMb by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val (a, t) = withContext(Dispatchers.Default) { DeviceInfo.availableRamMb(context) }
            availMb = a; totalMb = t
            delay(2000)
        }
    }
    // Estimasi RAM tiap model dihitung di Dispatchers.IO (membaca header GGUF).
    val estimates = remember { mutableStateMapOf<String, RamEstimate>() }
    LaunchedEffect(models) {
        models.forEach { m ->
            if (!estimates.containsKey(m.path)) {
                val e = withContext(Dispatchers.IO) { runCatching { RamEstimator.estimate(m, app.modelSettings.get(m.path, m.type)) }.getOrNull() }
                if (e != null) estimates[m.path] = e
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GA.Surface,
        scrimColor = Color.Black.copy(alpha = 0.6f),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pilih Model", color = GA.Strong, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onDismiss(); onOpenHub() }) {
                    Icon(Icons.Filled.Tune, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kelola")
                }
            }

            // ---------- Meteran RAM HP ----------
            if (totalMb > 0) {
                val used = (totalMb - availMb).coerceAtLeast(0)
                val frac by animateFloatAsState(used.toFloat() / totalMb, tween(500), label = "ramHp")
                Column(Modifier.padding(vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(GA.SurfaceHigh).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Memory, null, tint = accent.main, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("RAM HP", color = GA.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Bebas ${Fmt.gbFromMb(availMb)} GB dari ${Fmt.gbFromMb(totalMb)} GB", color = GA.TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Meter(frac, if (frac > 0.88f) GA.Red else if (frac > 0.75f) GA.Amber else accent.main)
                }
            }

            AnimatedVisibility(progress != null, enter = fadeIn()) {
                Text("Memuat model… ${((progress ?: 0f) * 100).toInt()}%", color = accent.main, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            }

            if (models.isEmpty()) {
                Text("Belum ada model. Ketuk Kelola → izinkan Akses Semua File, atau pilih file .gguf / .litertlm.",
                    color = GA.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
            }

            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(models, key = { it.path }) { m ->
                    val isLoaded = loaded?.path == m.path
                    val est = estimates[m.path]
                    // RAM yang tersedia untuk model ini: sisa sekarang + RAM model aktif lain yang akan dilepas dulu (Strict Unload-Before-Load).
                    val loadedOther = loaded?.takeIf { it.path != m.path }?.let { estimates[it.path]?.totalBytes } ?: 0L
                    val budgetBytes = availMb * 1024L * 1024L + loadedOther + if (isLoaded) (est?.totalBytes ?: 0L) else 0L
                    ModelOption(
                        model = m,
                        selected = active?.path == m.path,
                        isLoaded = isLoaded,
                        estimate = est,
                        budgetBytes = budgetBytes,
                        busy = status == ModelStatus.LOADING || status == ModelStatus.UNLOADING || status == ModelStatus.BUSY,
                        onUse = {
                            app.models.select(m)
                            app.llm.loadAsync(m, app.modelSettings.get(m.path, m.type), s.cpuThreads)
                        },
                        onUnload = { app.llm.unloadAsync() },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Meter(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(GA.SurfaceHigher)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
private fun ModelOption(
    model: ModelFile,
    selected: Boolean,
    isLoaded: Boolean,
    estimate: RamEstimate?,
    budgetBytes: Long,
    busy: Boolean,
    onUse: () -> Unit,
    onUnload: () -> Unit,
) {
    val accent = LocalAccent.current
    val ratio = if (estimate != null && budgetBytes > 0) estimate.totalBytes.toFloat() / budgetBytes else 0f
    val meterColor by animateColorAsState(
        when {
            estimate == null -> GA.TextSecondary
            ratio < 0.75f -> GA.Green
            ratio < 1f -> GA.Amber
            else -> GA.Red
        }, tween(400), label = "ramColor",
    )
    val frac by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(600), label = "ramFrac")
    val borderColor by animateColorAsState(if (isLoaded) GA.Green else if (selected) accent.main else GA.Outline, tween(300), label = "border")

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GA.SurfaceHigh)
            .border(if (isLoaded || selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(enabled = !busy && !isLoaded) { onUse() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoBadge(model.type.badge, if (model.type == EngineType.GGUF) GA.Violet else accent.main)
            Spacer(Modifier.width(6.dp))
            if (isLoaded) InfoBadge("● Aktif di RAM", GA.Green)
            Spacer(Modifier.weight(1f))
            Text("${Fmt.gb(model.sizeBytes)} GB", color = GA.TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(model.name, color = GA.Strong, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))

        // ---------- Meteran estimasi RAM model vs RAM tersedia ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(meterColor))
            Spacer(Modifier.width(6.dp))
            Text(
                if (estimate == null) "Menghitung perkiraan RAM…"
                else "Perkiraan RAM ${Fmt.gb(estimate.totalBytes)} GB • tersedia ${Fmt.gb(budgetBytes)} GB",
                color = GA.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Meter(frac, meterColor)
        if (estimate != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Bobot ${Fmt.gb(estimate.weightsBytes)} GB" +
                    (if (estimate.kvBytes > 0) " + KV cache ${Fmt.gb(estimate.kvBytes)} GB" else "") +
                    " + buffer ${Fmt.gb(estimate.overheadBytes)} GB" + if (ratio >= 1f) " — mungkin tidak muat!" else "",
                color = if (ratio >= 1f) GA.Red else GA.TextSecondary.copy(alpha = 0.8f), fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onUse, enabled = !busy && !isLoaded,
                colors = ButtonDefaults.buttonColors(containerColor = accent.main),
            ) { Text(if (isLoaded) "Sedang dipakai" else "Pakai") }
            if (isLoaded) OutlinedButton(onClick = onUnload, enabled = !busy) { Text("Lepas dari RAM") }
        }
    }
}
