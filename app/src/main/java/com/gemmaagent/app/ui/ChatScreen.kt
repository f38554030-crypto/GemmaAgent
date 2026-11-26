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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.gemmaagent.app.GemmaChatApp
import com.gemmaagent.app.chat.ChatController
import com.gemmaagent.app.chat.DefineState
import com.gemmaagent.app.data.ModelFile
import com.gemmaagent.app.data.SearchSource
import com.gemmaagent.app.data.db.MessageEntity
import com.gemmaagent.app.data.db.SessionEntity
import com.gemmaagent.app.docs.DocumentReader
import com.gemmaagent.app.docs.LoadedDocument
import com.gemmaagent.app.llm.ModelStatus
import com.gemmaagent.app.llm.Personas
import com.gemmaagent.app.llm.PolishStyle
import com.gemmaagent.app.llm.PromptBuilder
import com.gemmaagent.app.llm.PromptChains
import com.gemmaagent.app.llm.PromptTemplates
import com.gemmaagent.app.tools.Calculator
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.LocalAccent
import com.gemmaagent.app.util.DeviceInfo
import com.gemmaagent.app.util.Fmt
import com.gemmaagent.app.util.SpeechInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Telemetri RAM & suhu: dibaca hanya oleh composable kecil sehingga layar chat tidak ikut dirender ulang. */
@Stable
class Telemetry {
    var ramAvailMb by mutableLongStateOf(0L)
    var ramTotalMb by mutableLongStateOf(0L)
    var tempC by mutableFloatStateOf(0f)
    var breakDue by mutableStateOf(false)
    var sessionStart by mutableLongStateOf(System.currentTimeMillis())
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val keyboard = LocalSoftwareKeyboardController.current

    val s by app.settings.settings.collectAsState()
    val sessionId by chat.sessionId.collectAsState()
    val sessions by chat.sessions.collectAsState(initial = emptyList())
    val generating by chat.generating.collectAsState()
    val define by chat.define.collectAsState()
    val activeModel by app.models.active.collectAsState()
    val incognito by chat.incognito.collectAsState()

    // ---------- Input (TextFieldValue agar kursor selalu di akhir) & draf otomatis ----------
    var inputValue by rememberSaveable(sessionId, stateSaver = TextFieldValue.Saver) {
        val d = app.settings.loadDraft(sessionId)
        mutableStateOf(TextFieldValue(d, TextRange(d.length)))
    }
    val input = inputValue.text
    fun setInput(t: String) { inputValue = TextFieldValue(t, TextRange(t.length)) }
    LaunchedEffect(input, sessionId) {
        delay(400)
        app.settings.saveDraft(sessionId, input)
    }
    val focusRequester = remember { FocusRequester() }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    var pendingDoc by remember { mutableStateOf<LoadedDocument?>(null) }
    var compareNext by remember { mutableStateOf(false) }
    var zen by rememberSaveable { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }

    // ---------- Dialog & sheet ----------
    var showAttach by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
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
    LaunchedEffect(sharedText) {
        if (sharedText != null) { shared = sharedText; onSharedConsumed() }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { chat.events.collect { snackbar.showSnackbar(it) } }
    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    // ---------- Telemetri (fitur 33 & 36) — ditulis di sini, dibaca di composable kecil ----------
    val telemetry = remember { Telemetry() }
    LaunchedEffect(Unit) {
        while (true) {
            val (a, t) = withContext(Dispatchers.Default) { DeviceInfo.availableRamMb(context) }
            telemetry.ramAvailMb = a; telemetry.ramTotalMb = t
            telemetry.tempC = withContext(Dispatchers.Default) { DeviceInfo.batteryTempC(context) } ?: 0f
            if (System.currentTimeMillis() - telemetry.sessionStart > 45 * 60_000L) telemetry.breakDue = true
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

    // ---------- Input suara ----------
    val speech = remember {
        SpeechInput(context, onPartial = { setInput(it) }, onFinal = { setInput(it) },
            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
            onListeningChanged = { listening = it })
    }
    DisposableEffect(Unit) { onDispose { speech.stop() } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start() else toast("Izin mikrofon diperlukan untuk input suara")
    }

    // ---------- Pintasan layar utama ----------
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
        if (text == input) setInput("")
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

    fun prefill(text: String) {
        setInput(text)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    /** Kartu saran cepat di layar awal. */
    fun onSuggestion(id: Int) {
        when (id) {
            0 -> {
                app.settings.update { it.copy(online = true, searchSource = SearchSource.NEWS) }
                send("Apa berita terkini hari ini di Indonesia? Rangkum 5 poin terpenting.")
            }
            1 -> clipboardText()?.let { send(PromptBuilder.clipboard("summary", it.take(6000))) } ?: toast("Clipboard kosong — salin teks dulu.")
            2 -> prefill("Bantu saya brainstorming 10 ide kreatif tentang ")
            3 -> prefill("Jelaskan konsep berikut dengan bahasa sederhana, analogi sehari-hari, dan satu contoh nyata: ")
        }
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
    val currentTitle = if (incognito) "Sesi Incognito" else sessions.firstOrNull { it.id == sessionId }?.title ?: "GemmaChat"

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
                AnimatedVisibility(!zen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Filled.Menu, "Riwayat percakapan", tint = GA.TextPrimary) }
                        Column(Modifier.weight(1f)) {
                            Text(currentTitle, color = GA.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ModelChip(activeModel) { showModelSheet = true }
                        }
                        IconButton(onClick = {
                            app.settings.update { it.copy(thinking = !it.thinking) }
                            toast(if (!s.thinking) "🧠 Mode Berpikir ON (lebih dalam, lebih lambat)" else "⚡ Mode Berpikir OFF (jawaban langsung)")
                        }) {
                            Crossfade(s.thinking, animationSpec = tween(250), label = "think") { on ->
                                Icon(Icons.Filled.Psychology, "Mode Berpikir", tint = if (on) GA.Violet else GA.TextSecondary)
                            }
                        }
                        Box {
                            IconButton(onClick = { personaMenu = true }) { Icon(Icons.Filled.Face, "Persona AI", tint = GA.TextSecondary) }
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
                                DropdownMenuItem(text = { Text("Pilih model") }, leadingIcon = { Icon(Icons.Filled.Memory, null) }, onClick = { overflowMenu = false; showModelSheet = true })
                                DropdownMenuItem(text = { Text("Bersihkan Cache RAM") }, leadingIcon = { Icon(Icons.Filled.CleaningServices, null) },
                                    onClick = { overflowMenu = false; app.llm.clearCache { toast("🧹 KV cache dilepas") } })
                                DropdownMenuItem(text = { Text("Mode Fokus (Zen)") }, leadingIcon = { Icon(Icons.Filled.Fullscreen, null) }, onClick = { overflowMenu = false; zen = true })
                                DropdownMenuItem(text = { Text("Ekspor .md") }, leadingIcon = { Icon(Icons.Filled.Description, null) }, enabled = sessionId != null && !incognito, onClick = {
                                    overflowMenu = false
                                    sessionId?.let { id -> scope.launch { exportText = app.export.exportSession(id, true); exportMd.launch("${safeName(currentTitle)}.md") } }
                                })
                                DropdownMenuItem(text = { Text("Ekspor .txt") }, leadingIcon = { Icon(Icons.Filled.Description, null) }, enabled = sessionId != null && !incognito, onClick = {
                                    overflowMenu = false
                                    sessionId?.let { id -> scope.launch { exportText = app.export.exportSession(id, false); exportTxt.launch("${safeName(currentTitle)}.txt") } }
                                })
                                DropdownMenuItem(text = { Text("Pengaturan") }, leadingIcon = { Icon(Icons.Filled.Settings, null) }, onClick = { overflowMenu = false; onOpenSettings() })
                            }
                        }
                    }
                }
                AnimatedVisibility(!zen) { TelemetryBadges(telemetry) }

                // ===================== BANNER =====================
                Banners(telemetry, incognito, onOpenModelSheet = { showModelSheet = true }, onRetry = { chat.preload() }, onExitIncognito = { chat.newSession() })

                // ===================== DAFTAR PESAN (dipecah per blok) =====================
                MessageList(
                    modifier = Modifier.weight(1f),
                    chat = chat,
                    actions = actions,
                    generating = generating,
                    zen = zen,
                    onExitZen = { zen = false },
                    empty = { EmptyState(activeModel, onSuggestion = { onSuggestion(it) }, onPickModel = { showModelSheet = true }) },
                )

                // ===================== FILTER SUMBER ONLINE =====================
                AnimatedVisibility(s.online && !zen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                AnimatedVisibility(pendingImage != null || pendingDoc != null || compareNext) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pendingImage?.let { PendingChip("Gambar (OCR)", Icons.Filled.Image) { pendingImage = null } }
                        pendingDoc?.let { PendingChip(it.name, Icons.Filled.Description) { pendingDoc = null } }
                        if (compareNext) PendingChip("Mode Banding", Icons.AutoMirrored.Filled.CompareArrows) { compareNext = false }
                    }
                }

                // ===================== FLOATING INPUT PILL =====================
                InputPill(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    online = s.online,
                    listening = listening,
                    generating = generating,
                    canSend = input.isNotBlank() || pendingImage != null || pendingDoc != null,
                    focusRequester = focusRequester,
                    onPlus = { showAttach = true },
                    onToggleOnline = {
                        app.settings.update { it.copy(online = !it.online) }
                        toast(if (!s.online) "🌐 Mode Online: mencari web sebelum menjawab" else "📴 Mode Offline: 100% di HP")
                    },
                    onMic = {
                        if (listening) speech.stop()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) speech.start()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onSend = { send() },
                    onStop = { chat.stop() },
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        }
    }

    // ===================== SHEET =====================
    if (showAttach) {
        AttachSheet(
            compareOn = compareNext,
            onDismiss = { showAttach = false },
            onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onCamera = { launchCamera() },
            onDocument = { docPicker.launch(arrayOf("application/pdf", "text/plain", "text/markdown")) },
            onTemplates = { showTemplates = true },
            onClipSummary = { clipboardText()?.let { send(PromptBuilder.clipboard("summary", it.take(6000))) } ?: toast("Clipboard kosong") },
            onClipTranslate = { clipboardText()?.let { send(PromptBuilder.clipboard("translate", it.take(6000))) } ?: toast("Clipboard kosong") },
            onPolish = { showPolish = true },
            onCalc = { showCalc = true },
            onChain = { showChain = true },
            onDiff = { showDiffTool = true },
            onKnowledge = { kbPicker.launch(arrayOf("application/pdf", "text/plain", "text/markdown")) },
            onCompare = { compareNext = !compareNext },
        )
    }
    if (showModelSheet) ModelSheet(onDismiss = { showModelSheet = false }, onOpenHub = onOpenModelHub)

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
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { setInput(t.text); showTemplates = false }.padding(10.dp),
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
            dismissButton = { TextButton(onClick = { shared = null; setInput(text.take(4000)) }) { Text("Tanya") } },
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
            DrawerLink("Basis Pengetahuan", Icons.AutoMirrored.Filled.LibraryBooks, onKnowledge)
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

// =====================================================================
// KOMPONEN LAYAR CHAT
// =====================================================================

/** Chip model aktif (ketuk → bottom sheet pemilih model). */
@Composable
private fun ModelChip(model: ModelFile?, onClick: () -> Unit) {
    val app = GemmaChatApp.instance
    val status by app.llm.status.collectAsState()
    val accent = LocalAccent.current
    Row(
        Modifier.padding(top = 2.dp).clip(RoundedCornerShape(50)).background(GA.SurfaceHigh).clickable(onClick = onClick)
            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BlinkingDot(
            active = status == ModelStatus.LOADING || status == ModelStatus.BUSY || status == ModelStatus.UNLOADING,
            color = accent.main, size = 6.dp,
            idleColor = when (status) { ModelStatus.READY -> GA.Green; ModelStatus.ERROR -> GA.Red; else -> GA.TextSecondary },
        )
        Spacer(Modifier.width(6.dp))
        Crossfade(targetState = "${model?.shortLabel ?: "Pilih model"} • ${status.label}", animationSpec = tween(250), label = "modelchip") { t ->
            Text(t, color = GA.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = GA.TextSecondary, modifier = Modifier.size(16.dp))
    }
}

/** Lencana RAM & suhu (hanya composable ini yang dirender ulang tiap 3 detik). */
@Composable
private fun TelemetryBadges(t: Telemetry) {
    if (t.ramTotalMb <= 0) return
    Row(Modifier.padding(start = 56.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val ratio = t.ramAvailMb.toFloat() / t.ramTotalMb
        InfoBadge("RAM ${Fmt.gbFromMb(t.ramAvailMb)}/${Fmt.gbFromMb(t.ramTotalMb)} GB",
            if (ratio < 0.12f) GA.Red else if (ratio < 0.25f) GA.Amber else GA.Green, Icons.Filled.Memory)
        if (t.tempC > 0f) InfoBadge("${Fmt.one(t.tempC.toDouble())}°C",
            if (t.tempC >= 42f) GA.Red else if (t.tempC >= 38f) GA.Amber else GA.Green, Icons.Filled.Thermostat)
    }
}

@Composable
private fun Banners(t: Telemetry, incognito: Boolean, onOpenModelSheet: () -> Unit, onRetry: () -> Unit, onExitIncognito: () -> Unit) {
    val app = GemmaChatApp.instance
    val status by app.llm.status.collectAsState()
    val error by app.llm.error.collectAsState()
    val active by app.models.active.collectAsState()
    val progress by app.llm.progress.collectAsState()
    val accent = LocalAccent.current
    AnimatedVisibility(active == null && status != ModelStatus.LOADING, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Banner("Belum ada model. Pilih file .gguf / .litertlm dari folder Download.", GA.Amber, "Pilih", onOpenModelSheet)
    }
    AnimatedVisibility(status == ModelStatus.ERROR && error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Banner(error ?: "", GA.Red, "Coba Lagi", onRetry)
    }
    AnimatedVisibility(progress != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val p by animateFloatAsState(progress ?: 0f, tween(300), label = "load")
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Memuat ${active?.name ?: "model"}… ${(p * 100).toInt()}%", color = GA.TextSecondary, fontSize = 11.sp)
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                color = accent.main, trackColor = GA.SurfaceHigher, drawStopIndicator = {})
        }
    }
    AnimatedVisibility(t.tempC >= 42f, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Banner("HP mulai panas (${Fmt.one(t.tempC.toDouble())}°C). Istirahatkan sejenak agar performa tetap stabil.", GA.Amber, null) {}
    }
    AnimatedVisibility(t.breakDue && t.tempC < 42f, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Banner("Sudah 45 menit mengobrol. Waktunya istirahat mata sebentar? 🙂", accent.main, "Tutup") {
            t.breakDue = false; t.sessionStart = System.currentTimeMillis()
        }
    }
    AnimatedVisibility(incognito, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Banner("🕶️ Incognito: pesan hanya di RAM, tidak masuk riwayat & memori, hilang saat keluar.", GA.Violet, "Keluar", onExitIncognito)
    }
}

/**
 * Daftar chat dengan baris-baris yang dipecah per blok + Smart Scroll Lock berbasis sentuhan:
 * - Jari menyentuh & menggeser (DragInteraction) → gulir otomatis langsung dikunci.
 * - Gulir berhenti di dasar (isScrollInProgress=false & !canScrollForward) → kunci dilepas.
 * - Selama terkunci & masih ada konten di bawah → tombol kapsul "↓ Ke bawah".
 */
@Composable
private fun MessageList(
    modifier: Modifier,
    chat: ChatController,
    actions: MessageActions,
    generating: Boolean,
    zen: Boolean,
    onExitZen: () -> Unit,
    empty: @Composable () -> Unit,
) {
    val rowsFlow = remember(chat) { chatRowsFlow(chat) }
    val rowsState by rowsFlow.collectAsState(initial = null)
    val rows = rowsState.orEmpty()
    val latestRows by rememberUpdatedState(rows)
    val speakingId by GemmaChatApp.instance.tts.speakingId.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var follow by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                // Jari menyentuh & menggeser → kunci gulir otomatis seketika.
                is DragInteraction.Start -> follow = false
                // Jari diangkat di dasar daftar (atau daftar masih pendek) → lepaskan kunci.
                is DragInteraction.Stop, is DragInteraction.Cancel -> if (!listState.canScrollForward) follow = true
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && !listState.canScrollForward) follow = true
        }
    }
    // Pesan baru dari pengguna → ikuti lagi ke bawah.
    val userCount = remember(rows) { rows.count { it is UserRow } }
    LaunchedEffect(userCount) {
        if (userCount > 0) {
            follow = true
            listState.scrollToEnd(latestRows.size)
        }
    }
    // Token baru → gulir hanya bila tidak terkunci & jari tidak sedang menyentuh.
    LaunchedEffect(rows) {
        if (follow && !listState.isScrollInProgress) listState.scrollToEnd(rows.size)
    }

    Box(modifier) {
        Crossfade(targetState = rowsState != null && rows.isEmpty(), animationSpec = tween(320), label = "empty") { isEmpty ->
            if (isEmpty) {
                empty()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                ) {
                    items(rows, key = { it.key }, contentType = { it.type }) { row ->
                        Box(Modifier.animateItem(fadeInSpec = tween(220), placementSpec = null, fadeOutSpec = null)) {
                            when (row) {
                                is UserRow -> UserRowView(row, actions, enabled = !generating)
                                is AiHeaderRow -> AiHeaderView(row)
                                is AiBlockRow -> AiBlockView(row, actions)
                                is AiSpecialRow -> AiSpecialView(row, actions)
                                is AiLiveRow -> AiLiveView(row)
                                is AiFooterRow -> AiFooterView(row, generating, speakingId == row.msg.id, actions)
                            }
                        }
                    }
                }
            }
        }

        val showPill by remember { derivedStateOf { !follow && listState.canScrollForward } }
        AnimatedVisibility(
            visible = showPill,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.85f) + slideInVertically { it / 2 },
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f) + slideOutVertically { it / 2 },
        ) {
            Row(
                Modifier.shadow(10.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(GA.SurfaceHigher)
                    .border(1.dp, GA.Outline, RoundedCornerShape(50))
                    .clickable {
                        follow = true
                        scope.launch { runCatching { listState.scrollToEnd(latestRows.size, animate = true) } }
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("↓ Ke bawah", color = GA.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (generating) {
                    Spacer(Modifier.width(8.dp))
                    TypingDots(LocalAccent.current.main, dotSize = 5.dp)
                }
            }
        }
        if (zen) {
            IconButton(
                onClick = onExitZen,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(GA.SurfaceHigh),
            ) { Icon(Icons.Filled.CloseFullscreen, "Keluar Mode Fokus", tint = GA.TextSecondary) }
        }
    }
}

/** Gulir ke ujung bawah daftar (instan saat streaming, animasi saat tombol "Ke bawah" ditekan). */
private suspend fun LazyListState.scrollToEnd(count: Int, animate: Boolean = false) {
    if (count <= 0) return
    val last = count - 1
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    if (animate) {
        if (lastVisible < last - 12) scrollToItem((last - 6).coerceAtLeast(0))
        animateScrollToItem(last, Int.MAX_VALUE / 4)
        scrollBy(1_000_000f)
    } else {
        if (lastVisible < last - 1) scrollToItem(last)
        scrollBy(1_000_000f)
    }
}

/** Bilah ketik kapsul melayang dengan border glow biru-ungu saat Mode Online aktif. */
@Composable
private fun InputPill(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    online: Boolean,
    listening: Boolean,
    generating: Boolean,
    canSend: Boolean,
    focusRequester: FocusRequester,
    onPlus: () -> Unit,
    onToggleOnline: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val accent = LocalAccent.current
    val glow by animateFloatAsState(if (online) 1f else 0f, tween(450), label = "glow")
    val shape = RoundedCornerShape(28.dp)
    val glowBrush = remember(glow) {
        Brush.linearGradient(listOf(GA.GBlue.copy(alpha = glow), GA.Violet.copy(alpha = glow), GA.Rose.copy(alpha = glow * 0.9f)))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(
                elevation = (4f + 14f * glow).dp, shape = shape,
                ambientColor = GA.GBlue.copy(alpha = 0.2f + 0.5f * glow), spotColor = GA.Violet.copy(alpha = 0.25f + 0.6f * glow),
            )
            .clip(shape)
            .background(GA.SurfaceHigh)
            .border(1.dp, GA.Outline.copy(alpha = 1f - glow), shape)
            .then(if (glow > 0.01f) Modifier.border(1.5.dp, glowBrush, shape) else Modifier)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        CircleButton(Icons.Filled.Add, "Lampiran & alat", Color.Transparent, accent.main, onPlus)
        CircleButton(
            Icons.Filled.Public, if (online) "Mode Online aktif" else "Mode Offline",
            if (online) accent.main.copy(alpha = 0.18f) else Color.Transparent,
            if (online) accent.main else GA.TextSecondary, onToggleOnline,
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(if (listening) "Mendengarkan…" else if (online) "Tanya apa saja (online)…" else "Tanya Gemma…", color = GA.TextSecondary, fontSize = 15.sp)
            },
            modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 160.dp).focusRequester(focusRequester),
            textStyle = TextStyle(fontSize = 15.sp, color = GA.TextPrimary),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                focusedTextColor = GA.TextPrimary, unfocusedTextColor = GA.TextPrimary, cursorColor = accent.main,
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
            maxLines = 6,
        )
        CircleButton(if (listening) Icons.Filled.Mic else Icons.Filled.MicNone, "Input suara",
            if (listening) GA.Red.copy(alpha = 0.2f) else Color.Transparent, if (listening) GA.Red else GA.TextSecondary, onMic)
        Crossfade(targetState = generating, animationSpec = tween(200), label = "sendstop") { gen ->
            if (gen) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(GA.TextPrimary).clickable(onClick = onStop),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Stop, "Stop generasi", tint = GA.Black, modifier = Modifier.size(20.dp)) }
            } else {
                val active by animateFloatAsState(if (canSend) 1f else 0f, tween(200), label = "send")
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (canSend) GA.Sparkle else Brush.linearGradient(listOf(GA.SurfaceHigher, GA.SurfaceHigher)))
                        .clickable(enabled = canSend, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.ArrowUpward, "Kirim", tint = Color.White.copy(alpha = 0.45f + 0.55f * active), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, desc: String, bg: Color, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** Lembar "+" terpadu: akses cepat Galeri, Dokumen PDF, Template Prompt + alat lain. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(
    compareOn: Boolean,
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onDocument: () -> Unit,
    onTemplates: () -> Unit,
    onClipSummary: () -> Unit,
    onClipTranslate: () -> Unit,
    onPolish: () -> Unit,
    onCalc: () -> Unit,
    onChain: () -> Unit,
    onDiff: () -> Unit,
    onKnowledge: () -> Unit,
    onCompare: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun run(action: () -> Unit) { onDismiss(); action() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = GA.Surface, scrimColor = Color.Black.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).verticalScroll(rememberScrollState())) {
            Text("Tambahkan ke chat", color = GA.Strong, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AttachTile(Icons.Filled.Image, "Galeri", GA.GBlue, Modifier.weight(1f)) { run(onGallery) }
                AttachTile(Icons.Filled.PictureAsPdf, "Dokumen PDF", GA.Rose, Modifier.weight(1f)) { run(onDocument) }
                AttachTile(Icons.AutoMirrored.Filled.LibraryBooks, "Template", GA.Violet, Modifier.weight(1f)) { run(onTemplates) }
            }
            Spacer(Modifier.height(14.dp))
            ToolRow(Icons.Filled.CameraAlt, "Kamera (OCR offline)") { run(onCamera) }
            ToolRow(Icons.Filled.ContentPaste, "Tempel & Ringkas clipboard") { run(onClipSummary) }
            ToolRow(Icons.Filled.Translate, "Tempel & Terjemahkan clipboard") { run(onClipTranslate) }
            ToolRow(Icons.Filled.Spellcheck, "Poles Gaya Bahasa") { run(onPolish) }
            ToolRow(Icons.Filled.Calculate, "Kalkulator & Konverter") { run(onCalc) }
            ToolRow(Icons.Filled.Link, "Rantai Prompt") { run(onChain) }
            ToolRow(Icons.Filled.Difference, "Diff Viewer (bandingkan teks)") { run(onDiff) }
            ToolRow(Icons.Filled.LibraryAdd, "Tambah ke Basis Pengetahuan") { run(onKnowledge) }
            ToolRow(Icons.AutoMirrored.Filled.CompareArrows, if (compareOn) "Mode Banding: AKTIF (ketuk untuk mematikan)" else "Mode Banding (Cepat vs Mendalam)") { run(onCompare) }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AttachTile(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(96.dp).clip(RoundedCornerShape(20.dp)).background(GA.SurfaceHigh)
            .border(1.dp, GA.Outline, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(label, color = GA.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToolRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = GA.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = GA.TextPrimary, fontSize = 14.sp)
    }
}

/** Layar awal: sapaan minimalis bergradasi + status model + grid 2×2 kartu saran cepat. */
@Composable
private fun EmptyState(model: ModelFile?, onSuggestion: (Int) -> Unit, onPickModel: () -> Unit) {
    val status by GemmaChatApp.instance.llm.status.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(24.dp))
        SparkleAvatar(size = 56.dp)
        Spacer(Modifier.height(18.dp))
        Text(
            "Halo! Ada yang bisa dibantu?",
            style = TextStyle(brush = GA.Sparkle, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(GA.SurfaceHigh).clickable(onClick = onPickModel).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(
                when (status) { ModelStatus.READY -> GA.Green; ModelStatus.ERROR -> GA.Red; ModelStatus.LOADING, ModelStatus.BUSY -> GA.Amber; else -> GA.TextSecondary }
            ))
            Spacer(Modifier.width(8.dp))
            Text(
                if (model == null) "Belum ada model — ketuk untuk memilih" else "${model.shortLabel} • ${model.type.badge} • ${status.label}",
                color = GA.TextSecondary, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SuggestionCard(Icons.Filled.Newspaper, "Cari Berita Terkini", "Pencarian web online", GA.GBlue, Modifier.weight(1f)) { onSuggestion(0) }
            SuggestionCard(Icons.Filled.ContentPaste, "Ringkas Teks Clipboard", "Dari teks yang disalin", GA.Rose, Modifier.weight(1f)) { onSuggestion(1) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SuggestionCard(Icons.Filled.Lightbulb, "Bantu Brainstorming Ide", "Kumpulkan ide kreatif", GA.Amber, Modifier.weight(1f)) { onSuggestion(2) }
            SuggestionCard(Icons.Filled.School, "Jelaskan Konsep Rumit", "Bahasa sederhana", GA.Violet, Modifier.weight(1f)) { onSuggestion(3) }
        }
        Spacer(Modifier.height(16.dp))
        Text("Tip: tahan sebuah kata di jawaban AI untuk melihat artinya.", color = GA.TextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

@Composable
private fun SuggestionCard(icon: ImageVector, title: String, subtitle: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(116.dp).clip(RoundedCornerShape(22.dp)).background(GA.SurfaceHigh)
            .border(1.dp, GA.Outline, RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Column {
            Text(title, color = GA.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = GA.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
