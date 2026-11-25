package com.gemmaagent.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.gemmaagent.app.GemmaAgentApp
import com.gemmaagent.app.data.ChatMessage
import com.gemmaagent.app.data.Role
import com.gemmaagent.app.data.TaskStatus
import com.gemmaagent.app.llm.LlmEngine
import com.gemmaagent.app.llm.ModelStatus
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.util.DeviceInfo
import com.gemmaagent.app.util.Fmt
import com.gemmaagent.app.util.SpeechInput
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class QuickAction(val label: String, val command: String, val icon: ImageVector)

private val quickActions = listOf(
    QuickAction("Buka WhatsApp", "Buka aplikasi WhatsApp", Icons.Filled.TouchApp),
    QuickAction("Cek Baterai & Wi-Fi", "Berapa persen baterai HP saya dan apakah Wi-Fi tersambung?", Icons.Filled.BatteryStd),
    QuickAction("Bersihkan Notifikasi", "Buka panel notifikasi lalu ketuk tombol hapus semua notifikasi", Icons.Filled.ClearAll),
    QuickAction("Buka Pengaturan", "Buka aplikasi Pengaturan", Icons.Filled.Settings),
    QuickAction("Ke Beranda", "Kembali ke layar beranda HP", Icons.Filled.Home),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val app = GemmaAgentApp.instance
    val agent = app.agent

    val messages by app.chat.messages.collectAsState()
    val running by agent.running.collectAsState()
    val statusLine by agent.statusLine.collectAsState()
    val log by agent.log.collectAsState()
    val liveThought by agent.liveThought.collectAsState()
    val modelStatus by app.llm.status.collectAsState()
    val modelError by app.llm.error.collectAsState()

    var input by rememberSaveable { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    var logExpanded by rememberSaveable { mutableStateOf(true) }
    var listening by remember { mutableStateOf(false) }

    // ---- Monitor RAM real-time ----
    var ramAvail by remember { mutableLongStateOf(0L) }
    var ramTotal by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val (a, t) = DeviceInfo.availableRamMb(context)
            ramAvail = a; ramTotal = t
            delay(2000)
        }
    }

    // ---- Input suara ----
    val speech = remember {
        SpeechInput(
            context,
            onPartial = { input = it },
            onFinal = { input = it },
            onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
            onListeningChanged = { listening = it },
        )
    }
    DisposableEffect(Unit) { onDispose { speech.stop() } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start()
        else Toast.makeText(context, "Izin mikrofon diperlukan untuk input suara", Toast.LENGTH_SHORT).show()
    }

    val send: (String) -> Unit = { text ->
        if (text.isNotBlank() && !running) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            app.feedback.clickHaptic()
            agent.runCommand(text)
            input = ""
        }
    }

    // ---- Auto-scroll ke pesan terbaru ----
    val listState = rememberLazyListState()
    val lastActionsSize = messages.lastOrNull()?.actions?.size ?: 0
    LaunchedEffect(messages.size, lastActionsSize, running) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(GA.Black)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // ================= TOP APP BAR =================
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke Beranda", tint = GA.TextPrimary)
                }
            },
            title = {
                Column {
                    Text("Gemma Agent", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GA.TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BlinkingDot(
                            active = modelStatus == ModelStatus.LOADING || modelStatus == ModelStatus.BUSY,
                            color = if (modelStatus == ModelStatus.ERROR) GA.Red else GA.Blue,
                            size = 7.dp,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "${LlmEngine.MODEL_LABEL} • ${modelStatus.label}",
                            fontSize = 11.sp, color = GA.TextSecondary, maxLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        RamBadge(ramAvail, ramTotal)
                    }
                }
            },
            actions = {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showResetDialog = true
                }) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "Sesi Baru / Reset Memori", tint = GA.Violet)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = GA.Black),
        )

        if (modelStatus == ModelStatus.ERROR && modelError != null) {
            Row(
                Modifier.fillMaxWidth().background(GA.Red.copy(alpha = 0.1f)).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(modelError ?: "", color = GA.Red, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    app.feedback.clickHaptic()
                    app.llm.loadAsync()
                }) { Text("Coba Lagi") }
            }
        }

        // ================= DAFTAR CHAT =================
        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        Box(Modifier.animateItem()) {
                            if (msg.role == Role.USER) UserBubble(msg)
                            else AgentBubble(msg, statusLine = statusLine, liveThought = liveThought, isLive = running && msg.status == TaskStatus.RUNNING)
                        }
                    }
                }
            }
        }

        // ================= LOG PEMIKIRAN AI =================
        AnimatedVisibility(
            visible = running || log.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ThoughtLogPanel(log = log, running = running, expanded = logExpanded, onToggle = { logExpanded = !logExpanded })
        }

        // ================= QUICK ACTION CHIPS =================
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            items(quickActions) { qa ->
                AssistChip(
                    onClick = { send(qa.command) },
                    enabled = !running,
                    label = { Text(qa.label, fontSize = 13.sp) },
                    leadingIcon = { Icon(qa.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(50),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = GA.SurfaceHigh,
                        labelColor = GA.TextPrimary,
                        leadingIconContentColor = GA.Blue,
                    ),
                    border = AssistChipDefaults.assistChipBorder(enabled = !running, borderColor = GA.Outline),
                )
            }
        }

        // ================= KOLOM KETIK =================
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicButton(listening = listening) {
                app.feedback.clickHaptic()
                if (listening) {
                    speech.stop()
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    speech.start()
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            Spacer(Modifier.width(6.dp))
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(if (listening) "Mendengarkan…" else "Perintahkan agen…", color = GA.TextSecondary) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 140.dp),
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GA.SurfaceHigh,
                    unfocusedContainerColor = GA.SurfaceHigh,
                    disabledContainerColor = GA.SurfaceHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = GA.TextPrimary,
                    unfocusedTextColor = GA.TextPrimary,
                    cursorColor = GA.Blue,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                maxLines = 4,
            )
            Spacer(Modifier.width(6.dp))
            if (running) {
                RoundButton(icon = Icons.Filled.Stop, description = "STOP AGEN", background = Brush.linearGradient(listOf(GA.Red, Color(0xFFD62839)))) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    agent.stop()
                }
            } else {
                RoundButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    description = "Kirim perintah",
                    background = Brush.linearGradient(listOf(GA.Blue, GA.VioletDeep)),
                    enabled = input.isNotBlank(),
                ) { send(input) }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = GA.SurfaceHigh,
            title = { Text("Sesi Baru / Reset Memori?", color = GA.TextPrimary) },
            text = { Text("Riwayat chat dan memori konteks AI akan dikosongkan.", color = GA.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    app.feedback.clickHaptic()
                    agent.stop()
                    app.chat.resetSession()
                    showResetDialog = false
                    Toast.makeText(context, "Sesi baru dimulai, memori AI dikosongkan", Toast.LENGTH_SHORT).show()
                }) { Text("Reset", color = GA.Red) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun RamBadge(availMb: Long, totalMb: Long) {
    if (totalMb <= 0) return
    val ratio = availMb.toFloat() / totalMb
    val color = when {
        ratio < 0.12f -> GA.Red
        ratio < 0.25f -> GA.Amber
        else -> GA.Green
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Memory, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text("RAM " + Fmt.gbFromMb(availMb) + "/" + Fmt.gbFromMb(totalMb) + " GB", fontSize = 10.sp, color = color, maxLines = 1)
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(GA.Blue, GA.VioletDeep))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("Halo! Saya Gemma Agent", color = GA.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Ketik atau ucapkan perintah, atau ketuk salah satu tombol cepat di bawah. Semua diproses offline di HP ini.",
            color = GA.TextSecondary, fontSize = 14.sp,
        )
    }
}

@Composable
private fun UserBubble(msg: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                    .background(Brush.linearGradient(listOf(GA.BlueDeep, GA.VioletDeep)))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(msg.text, color = Color.White, fontSize = 15.sp)
            }
            Text(timeOf(msg.timestamp), color = GA.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp, end = 4.dp))
        }
    }
}

@Composable
private fun AgentBubble(msg: ChatMessage, statusLine: String, liveThought: String, isLive: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(GA.Blue, GA.VioletDeep))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.widthIn(max = 310.dp)) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(GA.SurfaceHigh)
                    .border(1.dp, GA.Outline, RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .animateContentSize()
            ) {
                if (isLive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypingDots()
                        Spacer(Modifier.width(10.dp))
                        Text(statusLine, color = GA.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (liveThought.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(liveThought.takeLast(160), color = GA.Violet.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(msg.text, color = GA.TextPrimary, fontSize = 15.sp)
                }
                if (msg.actions.isNotEmpty() || (msg.status != null && msg.status != TaskStatus.RUNNING)) {
                    Spacer(Modifier.height(8.dp))
                    ActionCard(msg)
                }
            }
            Text(timeOf(msg.timestamp), color = GA.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
        }
    }
}

/** Kartu status kecil yang merangkum aksi layar yang telah dieksekusi. */
@Composable
private fun ActionCard(msg: ChatMessage) {
    val (label, color) = when (msg.status) {
        TaskStatus.DONE -> "Selesai" to GA.Green
        TaskStatus.STOPPED -> "Dihentikan" to GA.Amber
        TaskStatus.ERROR -> "Error" to GA.Red
        TaskStatus.RUNNING -> "Berjalan" to GA.Blue
        null -> "Info" to GA.TextSecondary
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GA.Black)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${msg.actions.size} aksi", color = GA.TextSecondary, fontSize = 11.sp)
        }
        msg.actions.takeLast(15).forEachIndexed { i, a ->
            Text("${i + 1}. $a", color = GA.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun ThoughtLogPanel(log: List<com.gemmaagent.app.agent.LogEntry>, running: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GA.Surface)
            .border(1.dp, GA.Outline, RoundedCornerShape(16.dp))
            .animateContentSize()
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BlinkingDot(active = running, color = GA.Violet, size = 8.dp)
            Spacer(Modifier.width(8.dp))
            Text("Log Pemikiran AI", color = GA.Violet, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, contentDescription = null, tint = GA.TextSecondary)
        }
        if (expanded) {
            val listState = rememberLazyListState()
            LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 130.dp).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            ) {
                items(log) { entry ->
                    Row(Modifier.animateItem().padding(vertical = 2.dp)) {
                        Text(timeOf(entry.time, seconds = true), color = GA.TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(entry.text, color = logColor(entry.kind), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(1f, 1.15f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "micPulse")
    Box(
        Modifier
            .size(48.dp)
            .scale(if (listening) pulse else 1f)
            .clip(CircleShape)
            .background(if (listening) GA.Red.copy(alpha = 0.2f) else GA.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (listening) Icons.Filled.Mic else Icons.Filled.MicNone,
            contentDescription = "Input suara",
            tint = if (listening) GA.Red else GA.Violet,
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, background: Brush, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (enabled) background else Brush.linearGradient(listOf(GA.SurfaceHigher, GA.SurfaceHigher)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = Color.White) }
}

private fun timeOf(ts: Long, seconds: Boolean = false): String =
    SimpleDateFormat(if (seconds) "HH:mm:ss" else "HH:mm", Locale("id", "ID")).format(Date(ts))
