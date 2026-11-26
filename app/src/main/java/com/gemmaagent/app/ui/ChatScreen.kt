package com.gemmaagent.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.LinearProgressIndicator
import com.gemmaagent.app.llm.PromptChains
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.gemmaagent.app.GemmaChatApp
import com.gemmaagent.app.chat.DefineState
import com.gemmaagent.app.data.SearchSource
import com.gemmaagent.app.data.db.MessageEntity
import com.gemmaagent.app.data.db.Roles
import com.gemmaagent.app.data.db.SessionEntity
import com.gemmaagent.app.docs.DocumentReader
import com.gemmaagent.app.docs.LoadedDocument
import com.gemmaagent.app.llm.ModelStatus
import com.gemmaagent.app.llm.Personas
import com.gemmaagent.app.llm.PolishStyle
import com.gemmaagent.app.llm.PromptBuilder
import com.gemmaagent.app.llm.PromptTemplates
import com.gemmaagent.app.tools.Calculator
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.LocalAccent
import com.gemmaagent.app.util.DeviceInfo
import com.gemmaagent.app.util.Fmt
import com.gemmaagent.app.util.SpeechInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenModelHub: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenKnowledge: () -> Unit,
    shortcut: String?,
    onShortcutConsumed: () -> Unit,
    sharedText: String?,
    onSharedConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val app = GemmaChatApp.instance
    val chat = app.chat
    val scope = rememberCoroutineScope()
    val accent = LocalAccent.current

    val s by app.settings.settings.collectAsState()
    val sessionId by chat.sessionId.collectAsState()
    val messages by chat.messages.collectAsState(initial = emptyList())
    val sessions by chat.sessions.collectAsState(initial = emptyList())
    val stream by chat.stream.collectAsState()
    val generating by chat.generating.collectAsState()
    val define by chat.define.collectAsState()
    val status by app.llm.status.collectAsState()
    val llmError by app.llm.error.collectAsState()
    val activeModel by app.models.active.collectAsState()
    val speakingId by app.tts.speakingId.collectAsState()

    // ---------- Input & draf (fitur 24) ----------
    var input by rememberSaveable(sessionId) { mutableStateOf(app.settings.loadDraft(sessionId)) }
    LaunchedEffect(input, sessionId) {
        delay(400)
        app.settings.saveDraft(sessionId, input)
    }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    var pendingDoc by remember { mutableStateOf<LoadedDocument?>(null) }
    var compareNext by remember { mutableStateOf(false) }
    var zen by rememberSaveable { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }

    // ---------- Dialog ----------
    var attachMenu by remember { mutableStateOf(false) }
    var personaMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showPolish by remember { mutableStateOf(false) }
    var showCalc by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MessageEntity?>(null) }
    var renaming by remember { mutableStateOf<SessionEntity?>(null) }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var diffPair by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showDiffTool by remember { mutableStateOf(false) }
    var showChain by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf<String?>(null) }
    val incognito by chat.incognito.collectAsState()
    val loadProgress by app.llm.progress.collectAsState()
    LaunchedEffect(sharedText) {
        if (sharedText != null) { shared = sharedText; onSharedConsumed() }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { chat.events.collect { snackbar.showSnackbar(it) } }
    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    // ---------- Telemetri RAM & suhu (fitur 33 & 36) ----------
    var ramAvail by remember { mutableLongStateOf(0L) }
    var ramTotal by remember { mutableLongStateOf(0L) }
    var temp by remember { mutableFloatStateOf(0f) }
    val sessionStart = remember { mutableLongStateOf(System.currentTimeMillis()) }
    var breakDue by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val (a, t) = DeviceInfo.availableRamMb(context)
            ramAvail = a; ramTotal = t
            temp = DeviceInfo.batteryTempC(context) ?: 0f
            if (System.currentTimeMillis() - sessionStart.longValue > 45 * 60_000L) breakDue = true
            delay(3000)
        }
    }

    // ---------- Launcher ----------
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) pendingImage = uri }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pendingImage = cameraUri }
    fun launchCamera() {
        runCatching {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val f = File(dir, "foto_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
            cameraUri = uri
            camera.launch(uri)
        }.onFailure { toast("Kamera tidak bisa dibuka: ${it.message}") }
    }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            toast("Membaca dokumen…")
            runCatching { DocumentReader.read(context, uri) }
                .onSuccess { d ->
                    if (d.text.isBlank()) toast("Dokumen kosong / teksnya tidak bisa diekstrak (mungkin hasil scan gambar).")
                    else {
                        pendingDoc = d
                        if (d.truncated) toast("Dokumen panjang: hanya bagian awal yang dibaca.")
                    }
                }
                .onFailure { toast("Gagal membaca dokumen: ${it.message}") }
        }
    }
    val kbPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { DocumentReader.read(context, uri) }
                .onSuccess { d -> if (d.text.isBlank()) toast("Teks dokumen tidak bisa diekstrak.") else chat.addToKnowledge(d) }
                .onFailure { toast("Gagal membaca dokumen: ${it.message}") }
        }
    }
    var exportText by remember { mutableStateOf("") }
    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) scope.launch { runCatching { app.export.writeText(uri, exportText) }.onSuccess { toast("Percakapan diekspor (.md)") }.onFailure { toast("Gagal ekspor: ${it.message}") } }
    }
    val exportTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch { runCatching { app.export.writeText(uri, exportText) }.onSuccess { toast("Percakapan diekspor (.txt)") }.onFailure { toast("Gagal ekspor: ${it.message}") } }
    }

    // ---------- Input suara (fitur 41) ----------
    val speech = remember {
        SpeechInput(context, onPartial = { input = it }, onFinal = { input = it },
            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
            onListeningChanged = { listening = it })
    }
    DisposableEffect(Unit) { onDispose { speech.stop() } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start() else toast("Izin mikrofon diperlukan untuk input suara")
    }

    // ---------- Pintasan layar utama (fitur 49) ----------
    LaunchedEffect(shortcut) {
        when (shortcut) {
            "new_chat" -> chat.newSession()
            "online" -> { chat.newSession(); app.settings.update { it.copy(online = true) } }
            "scan_image" -> { chat.newSession(); launchCamera() }
        }
        if (shortcut != null) onShortcutConsumed()
    }

    fun send(text: String = input) {
        if (generating) return
        if (text.isBlank() && pendingImage == null && pendingDoc == null) return
        chat.send(text, pendingImage, pendingDoc, compareNext)
        if (text == input) input = ""
        pendingImage = null
        pendingDoc = null
        compareNext = false
        app.settings.saveDraft(sessionId, "")
    }

    fun copy(text: String, label: String = "Teks disalin") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("GemmaChat", text))
        toast(label)
    }

    fun clipboardText(): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }

    val actions = remember {
        MessageActions(
            onCopy = { copy(it) },
            onCopyCode = { copy(it, "Kode disalin") },
            onSpeak = { app.tts.toggle(it.id, it.text) },
            onStar = { chat.toggleStar(it) },
            onSaveNote = { chat.saveNote(it) },
            onRegenerate = { chat.regenerate(it) },
            onShareCard = { m ->
                scope.launch {
                    runCatching { context.startActivity(app.export.shareQuoteCard(m.text, accent.main.toArgb())) }
                        .onFailure { toast("Gagal membuat kartu: ${it.message}") }
                }
            },
            onEdit = { editing = it },
            onRemember = { chat.addMemory(it.text); toast("🧠 Disimpan ke Memori AI") },
            onChecklist = { chat.makeChecklist(it) },
            onQuiz = { chat.makeStudy(it, quiz = true) },
            onFlashcards = { chat.makeStudy(it, quiz = false) },
            onToggleChecklist = { m, i -> chat.toggleChecklist(m, i) },
            onFollowUp = { q -> send(q) },
            onWord = { w, sentence -> chat.defineWord(w, sentence) },
            onPreviewCode = { l, c -> preview = l to c },
            onSelectVersion = { m, i -> chat.selectVersion(m, i) },
            onCompareVersions = { a, b -> diffPair = a to b },
        )
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val currentTitle = sessions.firstOrNull { it.id == sessionId }?.title ?: "GemmaChat Hybrid"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionsDrawer(
                sessions = sessions,
                currentId = sessionId,
                onNew = { chat.newSession(); scope.launch { drawerState.close() } },
                onSelect = { chat.setSession(it.id); scope.launch { drawerState.close() } },
                onPin = { chat.togglePin(it) },
                onRename = { renaming = it },
                onDelete = { chat.deleteSession(it.id) },
                onNotes = { scope.launch { drawerState.close() }; onOpenNotes() },
                onMemory = { scope.launch { drawerState.close() }; onOpenMemory() },
                onStats = { scope.launch { drawerState.close() }; onOpenStats() },
                onSettings = { scope.launch { drawerState.close() }; onOpenSettings() },
                onModelHub = { scope.launch { drawerState.close() }; onOpenModelHub() },
                onArchive = { scope.launch { drawerState.close() }; onOpenArchive() },
                onKnowledge = { scope.launch { drawerState.close() }; onOpenKnowledge() },
                onIncognito = { chat.startIncognito(); scope.launch { drawerState.close() } },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().background(GA.Black)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                // ===================== TOP APP BAR =====================
                AnimatedVisibility(!zen) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Filled.Menu, "Riwayat percakapan", tint = GA.TextPrimary) }
                        Column(Modifier.weight(1f)) {
                            Text(if (incognito) "🕶️ Sesi Incognito" else currentTitle, color = GA.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(
                                Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpenModelHub() },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                BlinkingDot(
                                    active = status == ModelStatus.LOADING || status == ModelStatus.BUSY,
                                    color = accent.main, size = 6.dp,
                                    idleColor = if (status == ModelStatus.READY) GA.Green else if (status == ModelStatus.ERROR) GA.Red else GA.TextSecondary,
                                )
                                activeModel?.let { InfoBadge(it.type.badge, accent.main) }
                                Text("${activeModel?.shortLabel ?: "Tanpa model"} • ${status.label}", color = GA.TextSecondary, fontSize = 10.sp, maxLines = 1)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                                if (ramTotal > 0) {
                                    val ratio = ramAvail.toFloat() / ramTotal
                                    InfoBadge("RAM ${Fmt.gbFromMb(ramAvail)}/${Fmt.gbFromMb(ramTotal)} GB",
                                        if (ratio < 0.12f) GA.Red else if (ratio < 0.25f) GA.Amber else GA.Green, Icons.Filled.Memory)
                                }
                                if (temp > 0f) InfoBadge("${Fmt.one(temp.toDouble())}°C",
                                    if (temp >= 42f) GA.Red else if (temp >= 38f) GA.Amber else GA.Green, Icons.Filled.Thermostat)
                            }
                        }
                        // Sakelar Thinking (fitur 6)
                        IconButton(onClick = {
                            app.settings.update { it.copy(thinking = !it.thinking) }
                            toast(if (!s.thinking) "🧠 Mode Berpikir ON (lebih dalam, lebih lambat)" else "⚡ Mode Berpikir OFF (jawaban langsung)")
                        }) {
                            Icon(Icons.Filled.Psychology, "Mode Berpikir", tint = if (s.thinking) GA.Violet else GA.TextSecondary)
                        }
                        // Persona (fitur 14)
                        Box {
                            IconButton(onClick = { personaMenu = true }) { Icon(Icons.Filled.Face, "Persona AI", tint = accent.main) }
                            DropdownMenu(expanded = personaMenu, onDismissRequest = { personaMenu = false }) {
                                Personas.all.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text("${p.emoji}  ${p.name}" + if (p.id == s.personaId) "  ✓" else "") },
                                        onClick = { personaMenu = false; app.settings.update { it.copy(personaId = p.id) }; toast("Persona: ${p.name}") },
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { overflowMenu = true }) { Icon(Icons.Filled.MoreVert, "Menu lainnya", tint = GA.TextPrimary) }
                            DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
                                DropdownMenuItem(text = { Text("Chat baru") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = { overflowMenu = false; chat.newSession() })
                                DropdownMenuItem(text = { Text(if (incognito) "Keluar Incognito" else "Sesi Incognito") }, leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                                    onClick = { overflowMenu = false; if (incognito) chat.newSession() else { chat.startIncognito(); toast("🕶️ Incognito: obrolan tidak disimpan") } })
                                DropdownMenuItem(text = { Text("Model Hub") }, leadingIcon = { Icon(Icons.Filled.Memory, null) }, onClick = { overflowMenu = false; onOpenModelHub() })
                                DropdownMenuItem(text = { Text("Bersihkan Cache RAM") }, leadingIcon = { Icon(Icons.Filled.CleaningServices, null) },
                                    onClick = { overflowMenu = false; app.llm.clearCache { toast("🧹 KV cache dilepas") } })
                                DropdownMenuItem(text = { Text("Mode Fokus (Zen)") }, leadingIcon = { Icon(Icons.Filled.Fullscreen, null) }, onClick = { overflowMenu = false; zen = true })
                                DropdownMenuItem(text = { Text("Ekspor .md") }, leadingIcon = { Icon(Icons.Filled.Description, null) }, enabled = sessionId != null, onClick = {
                                    overflowMenu = false
                                    sessionId?.let { id -> scope.launch { exportText = app.export.exportSession(id, true); exportMd.launch("${safeName(currentTitle)}.md") } }
                                })
                                DropdownMenuItem(text = { Text("Ekspor .txt") }, leadingIcon = { Icon(Icons.Filled.Description, null) }, enabled = sessionId != null, onClick = {
                                    overflowMenu = false
                                    sessionId?.let { id -> scope.launch { exportText = app.export.exportSession(id, false); exportTxt.launch("${safeName(currentTitle)}.txt") } }
                                })
                                DropdownMenuItem(text = { Text("Pengaturan") }, leadingIcon = { Icon(Icons.Filled.Settings, null) }, onClick = { overflowMenu = false; onOpenSettings() })
                            }
                        }
                    }
                }

                // ===================== BANNER =====================
                if (activeModel == null && status != ModelStatus.LOADING) {
                    Banner("Belum ada model. Buka Model Hub → izinkan Akses Semua File agar file .gguf / .litertlm di Download terdeteksi.", GA.Amber, "Model Hub") { onOpenModelHub() }
                } else if (status == ModelStatus.ERROR && llmError != null) {
                    Banner(llmError ?: "", GA.Red, "Coba Lagi") { chat.preload() }
                }
                if (temp >= 42f) {
                    Banner("HP mulai panas (${Fmt.one(temp.toDouble())}°C). Istirahatkan sejenak agar performa tetap stabil.", GA.Amber, null) {}
                } else if (breakDue) {
                    Banner("Sudah 45 menit mengobrol. Waktunya istirahat mata sebentar? 🙂", accent.main, "Tutup") {
                        breakDue = false; sessionStart.longValue = System.currentTimeMillis()
                    }
                }

                loadProgress?.let { p ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text("Memuat ${activeModel?.name ?: "model"}… ${(p * 100).toInt()}%", color = GA.TextSecondary, fontSize = 11.sp)
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)), color = accent.main, trackColor = GA.SurfaceHigher, drawStopIndicator = {})
                    }
                }
                if (incognito) {
                    Banner("🕶️ Incognito: pesan hanya di RAM, tidak masuk riwayat & memori, hilang saat keluar.", GA.Violet, "Keluar") { chat.newSession() }
                }

                // ===================== DAFTAR PESAN =====================
                // ---------- Smart Scroll Lock (fitur 27) ----------
                val listState = rememberLazyListState()
                var follow by remember { mutableStateOf(true) }
                val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
                val lockConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            // Jari menggeser ke bawah = pengguna membaca ke atas → kunci gulir otomatis.
                            if (source == NestedScrollSource.UserInput && available.y > 0f) follow = false
                            return Offset.Zero
                        }
                    }
                }
                LaunchedEffect(atBottom) { if (atBottom) follow = true }
                suspend fun jumpToEnd() {
                    if (messages.isEmpty()) return
                    val last = messages.size - 1
                    if ((listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) < last) listState.scrollToItem(last)
                    listState.scrollBy(100_000f)
                }
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) { follow = true; jumpToEnd() }
                }
                val lastPayload = messages.lastOrNull()?.payload
                LaunchedEffect(stream?.text?.length, stream?.steps?.size, lastPayload) {
                    if (follow) jumpToEnd()
                }
                Box(Modifier.weight(1f)) {
                    if (messages.isEmpty()) {
                        EmptyState(onTemplate = { input = it })
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize().nestedScroll(lockConnection),
                        ) {
                            items(messages, key = { it.id }) { m ->
                                Box(Modifier.animateItem()) {
                                    if (m.role == Roles.USER) UserBubble(m, actions, enabled = !generating)
                                    else AssistantBubble(
                                        m, stream, isLast = m.id == messages.last().id, generating = generating,
                                        speaking = speakingId == m.id, actions = actions,
                                    )
                                }
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = messages.isNotEmpty() && !atBottom && (!follow || !generating),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                    ) {
                        Row(
                            Modifier.shadow(6.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(GA.SurfaceHigher)
                                .border(1.dp, GA.Outline, RoundedCornerShape(50))
                                .clickable { follow = true; scope.launch { jumpToEnd() } }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("↓ Ke bawah", color = GA.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (zen) {
                        IconButton(
                            onClick = { zen = false },
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(GA.SurfaceHigh),
                        ) { Icon(Icons.Filled.CloseFullscreen, "Keluar Mode Fokus", tint = GA.TextSecondary) }
                    }
                }

                // ===================== FILTER SUMBER ONLINE (fitur 3) =====================
                AnimatedVisibility(s.online && !zen) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(SearchSource.entries) { src ->
                            FilterChip(
                                selected = s.searchSource == src,
                                onClick = { app.settings.update { it.copy(searchSource = src) } },
                                label = { Text(src.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.deep, selectedLabelColor = Color.White),
                            )
                        }
                    }
                }

                // ===================== LAMPIRAN TERTUNDA =====================
                if (pendingImage != null || pendingDoc != null || compareNext) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pendingImage?.let { PendingChip("Gambar", Icons.Filled.Image) { pendingImage = null } }
                        pendingDoc?.let { PendingChip(it.name, Icons.Filled.Description) { pendingDoc = null } }
                        if (compareNext) PendingChip("Mode Banding", Icons.Filled.CompareArrows) { compareNext = false }
                    }
                }

                // ===================== KOLOM INPUT =====================
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                        .shadow(10.dp, RoundedCornerShape(30.dp))
                        .clip(RoundedCornerShape(30.dp)).background(GA.SurfaceHigh)
                        .border(1.dp, GA.Outline, RoundedCornerShape(30.dp)).padding(5.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box {
                        RoundIcon(Icons.Filled.Add, "Lampiran & alat", Color.Transparent, accent.main) { attachMenu = true }
                        DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                            MenuItem("Kamera (OCR offline)", Icons.Filled.CameraAlt) { attachMenu = false; launchCamera() }
                            MenuItem("Galeri (OCR offline)", Icons.Filled.Image) {
                                attachMenu = false
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            MenuItem("Dokumen PDF / TXT", Icons.Filled.Description) {
                                attachMenu = false
                                docPicker.launch(arrayOf("application/pdf", "text/plain", "text/markdown"))
                            }
                            HorizontalDivider()
                            MenuItem("Tempel & Ringkas", Icons.Filled.ContentPaste) {
                                attachMenu = false
                                clipboardText()?.let { send(PromptBuilder.clipboard("summary", it.take(6000))) } ?: toast("Clipboard kosong")
                            }
                            MenuItem("Tempel & Terjemahkan", Icons.Filled.Translate) {
                                attachMenu = false
                                clipboardText()?.let { send(PromptBuilder.clipboard("translate", it.take(6000))) } ?: toast("Clipboard kosong")
                            }
                            HorizontalDivider()
                            MenuItem("Template Prompt", Icons.Filled.LibraryBooks) { attachMenu = false; showTemplates = true }
                            MenuItem("Poles Gaya Bahasa", Icons.Filled.Spellcheck) { attachMenu = false; showPolish = true }
                            MenuItem("Kalkulator & Konverter", Icons.Filled.Calculate) { attachMenu = false; showCalc = true }
                            MenuItem("Rantai Prompt", Icons.Filled.Link) { attachMenu = false; showChain = true }
                            MenuItem("Diff Viewer (bandingkan teks)", Icons.Filled.Difference) { attachMenu = false; showDiffTool = true }
                            MenuItem("Tambah ke Basis Pengetahuan", Icons.Filled.LibraryAdd) {
                                attachMenu = false
                                kbPicker.launch(arrayOf("application/pdf", "text/plain", "text/markdown"))
                            }
                            MenuItem(if (compareNext) "Mode Banding: AKTIF" else "Mode Banding (Cepat vs Mendalam)", Icons.Filled.CompareArrows) {
                                attachMenu = false; compareNext = !compareNext
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    // Sakelar Online (fitur 1)
                    RoundIcon(
                        if (s.online) Icons.Filled.Public else Icons.Filled.PublicOff,
                        if (s.online) "Mode Online aktif" else "Mode Offline",
                        if (s.online) accent.deep else Color.Transparent,
                        if (s.online) Color.White else GA.TextSecondary,
                    ) {
                        app.settings.update { it.copy(online = !it.online) }
                        toast(if (!s.online) "🌐 Mode Online: mencari web sebelum menjawab" else "📴 Mode Offline: 100% di HP")
                    }
                    Spacer(Modifier.width(4.dp))
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(if (listening) "Mendengarkan…" else if (s.online) "Tanya apa saja (online)…" else "Ketik pesan…", color = GA.TextSecondary, fontSize = 14.sp) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 150.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = GA.TextPrimary, unfocusedTextColor = GA.TextPrimary, cursorColor = accent.main,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        keyboardActions = KeyboardActions(),
                        maxLines = 5,
                    )
                    Spacer(Modifier.width(4.dp))
                    RoundIcon(if (listening) Icons.Filled.Mic else Icons.Filled.MicNone, "Input suara",
                        if (listening) GA.Red.copy(alpha = 0.25f) else Color.Transparent, if (listening) GA.Red else GA.Violet) {
                        if (listening) speech.stop()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.start()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    Spacer(Modifier.width(4.dp))
                    if (generating) {
                        RoundIcon(Icons.Filled.Stop, "Stop generasi", GA.Red, Color.White) { chat.stop() }
                    } else {
                        val canSend = input.isNotBlank() || pendingImage != null || pendingDoc != null
                        Box(
                            Modifier.size(46.dp).clip(CircleShape)
                                .background(if (canSend) GA.Sparkle else Brush.linearGradient(listOf(GA.SurfaceHigher, GA.SurfaceHigher)))
                                .clickable(enabled = canSend) { send() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Kirim", tint = Color.White) }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
        }
    }

    // ===================== DIALOG =====================
    define?.let { d ->
        AlertDialog(
            onDismissRequest = { chat.closeDefine() },
            containerColor = GA.SurfaceHigh,
            title = {
                Text("📖 " + when (d) { is DefineState.Loading -> d.word; is DefineState.Result -> d.word }, color = GA.TextPrimary)
            },
            text = {
                when (d) {
                    is DefineState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) { TypingDots(accent.main); Spacer(Modifier.width(8.dp)); Text("Mencari arti…", color = GA.TextSecondary) }
                    is DefineState.Result -> Text(d.text, color = GA.TextPrimary)
                }
            },
            confirmButton = { TextButton(onClick = { chat.closeDefine() }) { Text("Tutup") } },
        )
    }

    editing?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = GA.SurfaceHigh,
            title = { Text("Edit pesan", color = GA.TextPrimary) },
            text = {
                Column {
                    Text("Pesan ini dan semua balasan setelahnya akan diganti.", color = GA.TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp))
                }
            },
            confirmButton = { TextButton(onClick = { chat.editAndResend(m, text); editing = null }) { Text("Kirim ulang") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Batal") } },
        )
    }

    renaming?.let { sess ->
        var text by remember(sess.id) { mutableStateOf(sess.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = GA.SurfaceHigh,
            title = { Text("Ganti judul", color = GA.TextPrimary) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { chat.renameSession(sess.id, text); renaming = null }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Batal") } },
        )
    }

    if (showTemplates) {
        AlertDialog(
            onDismissRequest = { showTemplates = false },
            containerColor = GA.SurfaceHigh,
            title = { Text("Pustaka Template Prompt", color = GA.TextPrimary) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PromptTemplates.all.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { input = t.text; showTemplates = false }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t.emoji, fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(t.title, color = GA.TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(t.text, color = GA.TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTemplates = false }) { Text("Tutup") } },
        )
    }

    if (showPolish) {
        var text by remember { mutableStateOf(input.ifBlank { clipboardText().orEmpty() }) }
        var style by remember { mutableStateOf(PolishStyle.FORMAL) }
        AlertDialog(
            onDismissRequest = { showPolish = false },
            containerColor = GA.SurfaceHigh,
            title = { Text("Poles Gaya Bahasa", color = GA.TextPrimary) },
            text = {
                Column {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PolishStyle.entries) { st ->
                            FilterChip(selected = style == st, onClick = { style = st }, label = { Text(st.label) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it }, placeholder = { Text("Tulis / tempel teks…") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank() && !generating, onClick = {
                    showPolish = false
                    send(PromptBuilder.polish(style, text.take(4000)))
                }) { Text("Poles") }
            },
            dismissButton = { TextButton(onClick = { showPolish = false }) { Text("Batal") } },
        )
    }

    preview?.let { (l, c) -> CodePreviewDialog(l, c) { preview = null } }
    diffPair?.let { (a, b) -> DiffDialog(a, b, editable = false) { diffPair = null } }
    if (showDiffTool) DiffDialog("", "", editable = true) { showDiffTool = false }

    if (showChain) {
        var chainInput by remember { mutableStateOf(input) }
        var custom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChain = false },
            containerColor = GA.SurfaceHigh,
            title = { Text("🔗 Rantai Prompt", color = GA.TextPrimary) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(chainInput, { chainInput = it }, label = { Text("Teks / topik awal ({input})") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 180.dp))
                    Spacer(Modifier.height(8.dp))
                    PromptChains.all.forEach { ch ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp)).background(GA.Surface)
                                .clickable(enabled = chainInput.isNotBlank() && !generating) { showChain = false; chat.runChain(ch, chainInput) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(ch.emoji, fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(ch.title, color = GA.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(ch.steps.joinToString(" → ") { it.first }, color = GA.TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Rantai kustom (pisahkan langkah dengan baris ---; pakai {input} dan {prev}):", color = GA.TextSecondary, fontSize = 11.sp)
                    OutlinedTextField(custom, { custom = it }, placeholder = { Text("Ringkas: {input}\n---\nBuat 3 judul dari: {prev}") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = custom.isNotBlank() && chainInput.isNotBlank() && !generating, onClick = {
                    showChain = false; chat.runChain(PromptChains.custom(custom), chainInput)
                }) { Text("Jalankan kustom") }
            },
            dismissButton = { TextButton(onClick = { showChain = false }) { Text("Tutup") } },
        )
    }

    shared?.let { text ->
        AlertDialog(
            onDismissRequest = { shared = null },
            containerColor = GA.SurfaceHigh,
            title = { Text("Teks dari aplikasi lain", color = GA.TextPrimary) },
            text = { Text(text.take(600) + if (text.length > 600) "…" else "", color = GA.TextSecondary, fontSize = 13.sp, modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) },
            confirmButton = {
                Row {
                    TextButton(onClick = { shared = null; send(PromptBuilder.clipboard("summary", text.take(6000))) }) { Text("Ringkas") }
                    TextButton(onClick = { shared = null; send(PromptBuilder.clipboard("translate", text.take(6000))) }) { Text("Terjemahkan") }
                }
            },
            dismissButton = { TextButton(onClick = { shared = null; input = text.take(4000) }) { Text("Tanya") } },
        )
    }

    if (showCalc) {
        var expr by remember { mutableStateOf("") }
        val result = remember(expr) {
            if (expr.isBlank()) "" else Calculator.tryHandle(if (expr.contains(" ke ") || expr.contains(" to ")) expr else "hitung $expr")?.value
                ?: runCatching { Calculator.format(Calculator.evaluate(expr)) }.getOrElse { "…" }
        }
        AlertDialog(
            onDismissRequest = { showCalc = false },
            containerColor = GA.SurfaceHigh,
            title = { Text("🧮 Kalkulator & Konverter", color = GA.TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(expr, { expr = it }, singleLine = true, placeholder = { Text("mis. (12,5*4)^2 atau 10 km ke mil") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text(if (result.isBlank()) "Hasil muncul di sini" else "= $result", color = if (result.isBlank()) GA.TextSecondary else accent.main, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Fungsi: sqrt, sin, cos, tan, log, ln, abs, round, pi, e, %. Konversi: panjang, massa, volume, data, waktu, kecepatan, suhu (°C/°F/K).", color = GA.TextSecondary, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = expr.isNotBlank(), onClick = { showCalc = false; send(if (expr.contains(" ke ") || expr.contains(" to ")) expr else "hitung $expr") }) { Text("Kirim ke chat") }
            },
            dismissButton = { TextButton(onClick = { showCalc = false }) { Text("Tutup") } },
        )
    }
}

private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(40).ifBlank { "percakapan" }

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, bg: Color, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun PendingChip(label: String, icon: ImageVector, onRemove: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(GA.SurfaceHigher).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = LocalAccent.current.main, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = GA.TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.Filled.Close, "Hapus lampiran", tint = GA.TextSecondary, modifier = Modifier.size(18.dp).clickable(onClick = onRemove))
    }
}

@Composable
private fun Banner(text: String, color: Color, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.1f)).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = color) }
    }
}

@Composable
private fun EmptyState(onTemplate: (String) -> Unit) {
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(GA.Sparkle),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(14.dp))
        Text("GemmaChat Hybrid", color = GA.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Offline di HP • Online saat dibutuhkan", color = GA.TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        PromptTemplates.all.take(4).forEach { t ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(GA.Surface)
                    .border(1.dp, GA.Outline, RoundedCornerShape(14.dp)).clickable { onTemplate(t.text) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.emoji, fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(t.title, color = GA.TextPrimary, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Tip: tahan sebuah kata di jawaban AI untuk melihat artinya.", color = GA.TextSecondary, fontSize = 11.sp)
    }
}

/** Sidebar riwayat percakapan lengkap (fitur 23). */
@Composable
private fun SessionsDrawer(
    sessions: List<SessionEntity>,
    currentId: Long?,
    onNew: () -> Unit,
    onSelect: (SessionEntity) -> Unit,
    onPin: (SessionEntity) -> Unit,
    onRename: (SessionEntity) -> Unit,
    onDelete: (SessionEntity) -> Unit,
    onNotes: () -> Unit,
    onMemory: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onModelHub: () -> Unit,
    onArchive: () -> Unit,
    onKnowledge: () -> Unit,
    onIncognito: () -> Unit,
) {
    val app = GemmaChatApp.instance
    val accent = LocalAccent.current
    var query by remember { mutableStateOf("") }
    var filtered by remember { mutableStateOf(sessions) }
    var confirmDelete by remember { mutableStateOf<SessionEntity?>(null) }
    LaunchedEffect(query, sessions) {
        delay(200)
        filtered = app.chat.searchSessions(query, sessions)
    }
    ModalDrawerSheet(drawerContainerColor = GA.Surface, modifier = Modifier.width(310.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
            Text("Riwayat Percakapan", color = GA.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GA.Sparkle)
                    .clickable(onClick = onNew).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Chat Baru", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Cari kata kunci…") },
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { sess ->
                    var menu by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sess.id == currentId) GA.SurfaceHigher else Color.Transparent)
                            .clickable { onSelect(sess) }.padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (sess.pinned) Icon(Icons.Filled.PushPin, "Disematkan", tint = GA.Amber, modifier = Modifier.size(14.dp).padding(end = 2.dp))
                        Text(sess.title, color = GA.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opsi", tint = GA.TextSecondary) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(if (sess.pinned) "Lepas sematan" else "Sematkan") }, leadingIcon = { Icon(Icons.Filled.PushPin, null) }, onClick = { menu = false; onPin(sess) })
                                DropdownMenuItem(text = { Text("Ganti judul") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onRename(sess) })
                                DropdownMenuItem(text = { Text("Hapus", color = GA.Red) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = GA.Red) }, onClick = { menu = false; confirmDelete = sess })
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) item { Text(if (query.isBlank()) "Belum ada percakapan." else "Tidak ditemukan.", color = GA.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(8.dp)) }
            }
            HorizontalDivider(color = GA.Outline)
            DrawerLink("Catatan & Favorit", Icons.Filled.Bookmarks, onNotes)
            DrawerLink("Memori AI", Icons.Filled.Psychology, onMemory)
            DrawerLink("Model Hub", Icons.Filled.Memory, onModelHub)
            DrawerLink("Sesi Incognito", Icons.Filled.VisibilityOff, onIncognito)
            DrawerLink("Arsip Web Offline", Icons.Filled.Public, onArchive)
            DrawerLink("Basis Pengetahuan", Icons.Filled.LibraryBooks, onKnowledge)
            DrawerLink("Statistik", Icons.Filled.BarChart, onStats)
            DrawerLink("Pengaturan", Icons.Filled.Settings, onSettings)
        }
    }
    confirmDelete?.let { sess ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = GA.SurfaceHigh,
            title = { Text("Hapus percakapan?", color = GA.TextPrimary) },
            text = { Text("\"${sess.title}\" akan dihapus permanen.", color = GA.TextSecondary) },
            confirmButton = { TextButton(onClick = { onDelete(sess); confirmDelete = null }) { Text("Hapus", color = GA.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun DrawerLink(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = LocalAccent.current.main, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = GA.TextPrimary, fontSize = 14.sp)
    }
}
