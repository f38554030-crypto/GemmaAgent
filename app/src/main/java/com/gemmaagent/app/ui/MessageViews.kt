package com.gemmaagent.app.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemmaagent.app.chat.StreamState
import com.gemmaagent.app.data.db.Kinds
import com.gemmaagent.app.data.db.MessageEntity
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.LocalAccent
import com.gemmaagent.app.ui.theme.LocalFontScale
import com.gemmaagent.app.util.Fmt
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Aksi yang bisa dilakukan pada sebuah pesan. */
class MessageActions(
    val onCopy: (String) -> Unit,
    val onCopyCode: (String) -> Unit,
    val onSpeak: (MessageEntity) -> Unit,
    val onStar: (MessageEntity) -> Unit,
    val onSaveNote: (MessageEntity) -> Unit,
    val onRegenerate: (MessageEntity) -> Unit,
    val onShareCard: (MessageEntity) -> Unit,
    val onEdit: (MessageEntity) -> Unit,
    val onRemember: (MessageEntity) -> Unit,
    val onChecklist: (MessageEntity) -> Unit,
    val onQuiz: (MessageEntity) -> Unit,
    val onFlashcards: (MessageEntity) -> Unit,
    val onToggleChecklist: (MessageEntity, Int) -> Unit,
    val onFollowUp: (String) -> Unit,
    val onWord: (String, String) -> Unit,
)

private val timeFmt = SimpleDateFormat("HH:mm", Locale("id", "ID"))

@Composable
fun UserBubble(msg: MessageEntity, actions: MessageActions, enabled: Boolean) {
    val accent = LocalAccent.current
    val scale = LocalFontScale.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 310.dp)) {
            msg.imagePath?.let { ImageThumb(it) }
            if (msg.attachmentName != null && msg.imagePath == null) {
                AttachmentChip(msg.attachmentName)
                Spacer(Modifier.height(4.dp))
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                    .background(Brush.linearGradient(listOf(accent.deep, GA.VioletDeep)))
                    .clickable { menu = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(msg.text, color = Color.White, fontSize = (15 * scale).sp, lineHeight = (21 * scale).sp)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Salin") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        onClick = { menu = false; actions.onCopy(msg.text) })
                    DropdownMenuItem(text = { Text("Edit & kirim ulang") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        enabled = enabled, onClick = { menu = false; actions.onEdit(msg) })
                    DropdownMenuItem(text = { Text("Simpan ke Memori AI") }, leadingIcon = { Icon(Icons.Filled.Psychology, null) },
                        onClick = { menu = false; actions.onRemember(msg) })
                }
            }
            Text(timeFmt.format(Date(msg.createdAt)), color = GA.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp, end = 4.dp))
        }
    }
}

@Composable
private fun ImageThumb(path: String) {
    val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 }) }.getOrNull() }
    if (bmp != null) {
        Image(
            bmp.asImageBitmap(), contentDescription = "Gambar terlampir",
            contentScale = ContentScale.Crop,
            modifier = Modifier.padding(bottom = 4.dp).size(180.dp).clip(RoundedCornerShape(16.dp)),
        )
    } else AttachmentChip("Gambar", Icons.Filled.Image)
}

@Composable
fun AttachmentChip(name: String, icon: ImageVector = Icons.Filled.AttachFile) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(GA.SurfaceHigher).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = LocalAccent.current.main, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(name, color = GA.TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AssistantBubble(
    msg: MessageEntity,
    stream: StreamState?,
    isLast: Boolean,
    generating: Boolean,
    speaking: Boolean,
    actions: MessageActions,
) {
    val accent = LocalAccent.current
    val live = stream != null && stream.messageId == msg.id
    val payload = remember(msg.payload) { runCatching { JSONObject(msg.payload) }.getOrNull() }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent.main, GA.VioletDeep))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(if (msg.kind == Kinds.ERROR) GA.Red.copy(alpha = 0.1f) else GA.SurfaceHigh)
                    .border(1.dp, if (msg.kind == Kinds.ERROR) GA.Red.copy(alpha = 0.4f) else GA.Outline,
                        RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .animateContentSize()
            ) {
                if (live) {
                    LiveContent(stream!!)
                } else {
                    if (msg.thought.isNotBlank() && msg.kind != Kinds.COMPARE) ThoughtSection(msg.thought)
                    when (msg.kind) {
                        Kinds.QUIZ -> { Text(msg.text, color = GA.TextPrimary, fontSize = 14.sp); Spacer(Modifier.height(8.dp)); QuizView(payload) }
                        Kinds.FLASHCARDS -> { Text(msg.text, color = GA.TextPrimary, fontSize = 14.sp); Spacer(Modifier.height(8.dp)); FlashcardsView(payload) }
                        Kinds.CHECKLIST -> ChecklistView(msg, payload, actions)
                        Kinds.COMPARE -> CompareView(payload, actions)
                        else -> MarkdownText(msg.text, onWordLongPress = actions.onWord, onCopyCode = actions.onCopyCode)
                    }
                }
            }
            if (!live) {
                Citations(payload)
                MetaRow(msg)
                ActionRow(msg, isLast, generating, speaking, actions)
                FollowUps(payload, enabled = !generating && isLast, onClick = actions.onFollowUp)
            }
        }
    }
}

@Composable
private fun LiveContent(stream: StreamState) {
    val accent = LocalAccent.current.main
    stream.compareFast?.let {
        Text("⚡ Mode Cepat (selesai)", color = GA.TextSecondary, fontSize = 11.sp)
        Text(it.take(240) + if (it.length > 240) "…" else "", color = GA.TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
    }
    if (stream.thought.isNotBlank()) {
        Text("🧠 Proses berpikir…", color = GA.Violet, fontSize = 11.sp)
        Text(stream.thought.takeLast(300), color = GA.Violet.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
    }
    if (stream.text.isNotBlank()) {
        MarkdownText(stream.text)
        Spacer(Modifier.height(6.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TypingDots(accent)
        Spacer(Modifier.width(10.dp))
        Text(stream.phase, color = GA.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ThoughtSection(thought: String) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Psychology, null, tint = GA.Violet, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (open) "Sembunyikan proses berpikir" else "Lihat proses berpikir", color = GA.Violet, fontSize = 12.sp)
        }
        AnimatedVisibility(open) {
            Text(thought, color = GA.Violet.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun MetaRow(msg: MessageEntity) {
    if (msg.tokensPerSec <= 0 && !msg.online && msg.kind != Kinds.CALC) return
    Row(Modifier.padding(top = 4.dp, start = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (msg.tokensPerSec > 0) {
            InfoBadge("${String.format(Locale("id", "ID"), "%.1f", msg.tokensPerSec)} token/detik", GA.TextSecondary, Icons.Filled.Speed)
        }
        if (msg.durationMs > 0) InfoBadge("${Fmt.seconds(msg.durationMs)} dtk", GA.TextSecondary)
        if (msg.online) InfoBadge("Online", LocalAccent.current.main, Icons.Filled.Public)
        if (msg.kind == Kinds.CALC) InfoBadge("Kalkulator lokal", GA.Green)
    }
}

@Composable
private fun ActionRow(msg: MessageEntity, isLast: Boolean, generating: Boolean, speaking: Boolean, actions: MessageActions) {
    if (msg.kind == Kinds.ERROR) {
        if (isLast && !generating) {
            Row(Modifier.padding(top = 2.dp)) { SmallAction(Icons.Filled.Refresh, "Coba lagi") { actions.onRegenerate(msg) } }
        }
        return
    }
    var more by remember { mutableStateOf(false) }
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        SmallAction(Icons.Filled.ContentCopy, "Salin") { actions.onCopy(msg.text) }
        SmallAction(if (speaking) Icons.Filled.StopCircle else Icons.AutoMirrored.Filled.VolumeUp, if (speaking) "Hentikan suara" else "Bacakan") { actions.onSpeak(msg) }
        SmallAction(if (msg.starred) Icons.Filled.Star else Icons.Filled.StarBorder, "Bintang", tint = if (msg.starred) GA.Amber else null) { actions.onStar(msg) }
        SmallAction(Icons.Filled.BookmarkBorder, "Simpan ke Catatan") { actions.onSaveNote(msg) }
        if (isLast && !generating && msg.kind != Kinds.CHECKLIST) SmallAction(Icons.Filled.Refresh, "Regenerasi") { actions.onRegenerate(msg) }
        SmallAction(Icons.Filled.Share, "Bagikan sebagai kartu") { actions.onShareCard(msg) }
        Box {
            SmallAction(Icons.Filled.MoreHoriz, "Lainnya") { more = true }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(text = { Text("✅ Jadikan checklist") }, onClick = { more = false; actions.onChecklist(msg) })
                DropdownMenuItem(text = { Text("📝 Buat kuis") }, enabled = !generating, onClick = { more = false; actions.onQuiz(msg) })
                DropdownMenuItem(text = { Text("🃏 Buat flashcard") }, enabled = !generating, onClick = { more = false; actions.onFlashcards(msg) })
            }
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, desc: String, tint: Color? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = desc, tint = tint ?: GA.TextSecondary, modifier = Modifier.size(17.dp)) }
}

/** Kartu sitasi & tautan sumber (fitur 5). */
@Composable
private fun Citations(payload: JSONObject?) {
    val arr = payload?.optJSONArray("citations") ?: return
    if (arr.length() == 0) return
    val uri = LocalUriHandler.current
    val accent = LocalAccent.current.main
    Row(
        Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            val host = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
            Column(
                Modifier.width(170.dp).clip(RoundedCornerShape(12.dp)).background(GA.Surface)
                    .border(1.dp, GA.Outline, RoundedCornerShape(12.dp))
                    .clickable { runCatching { uri.openUri(url) } }.padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("[${i + 1}]", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Link, null, tint = GA.TextSecondary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(host, color = GA.TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(o.optString("title"), color = GA.TextPrimary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Saran pertanyaan lanjutan (fitur 45). */
@Composable
private fun FollowUps(payload: JSONObject?, enabled: Boolean, onClick: (String) -> Unit) {
    val arr = payload?.optJSONArray("followups") ?: return
    if (!enabled || arr.length() == 0) return
    val accent = LocalAccent.current.main
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (i in 0 until arr.length()) {
            val q = arr.optString(i)
            Text(
                "↳ $q",
                color = accent,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .clickable { onClick(q) }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Kuis pilihan ganda interaktif (fitur 18). */
@Composable
private fun QuizView(payload: JSONObject?) {
    val arr = payload?.optJSONArray("questions") ?: return
    val chosen = remember(payload) { mutableStateMapOf<Int, Int>() }
    val accent = LocalAccent.current.main
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (qi in 0 until arr.length()) {
            val q = arr.optJSONObject(qi) ?: continue
            val options = q.optJSONArray("options") ?: continue
            val answer = q.optInt("answer", -1)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GA.Black)
                    .border(1.dp, GA.Outline, RoundedCornerShape(14.dp)).padding(10.dp)
            ) {
                Text("${qi + 1}. ${q.optString("q")}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                for (oi in 0 until options.length()) {
                    val picked = chosen[qi]
                    val color = when {
                        picked == null -> GA.SurfaceHigher
                        oi == answer -> GA.Green.copy(alpha = 0.25f)
                        oi == picked -> GA.Red.copy(alpha = 0.25f)
                        else -> GA.SurfaceHigher
                    }
                    Text(
                        "${'A' + oi}. ${options.optString(oi)}",
                        color = GA.TextPrimary, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(color)
                            .clickable(enabled = picked == null) { chosen[qi] = oi }.padding(8.dp),
                    )
                }
                chosen[qi]?.let { p ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (p == answer) "✅ Benar! " else "❌ Kurang tepat. ") + q.optString("explain"),
                        color = if (p == answer) GA.Green else GA.Amber, fontSize = 12.sp,
                    )
                }
            }
        }
        val total = arr.length()
        if (chosen.size == total && total > 0) {
            var score = 0
            for (i in 0 until total) if (chosen[i] == arr.optJSONObject(i)?.optInt("answer", -1)) score++
            Text("Skor: $score / $total", color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

/** Flashcard bolak-balik (fitur 18). */
@Composable
private fun FlashcardsView(payload: JSONObject?) {
    val arr = payload?.optJSONArray("cards") ?: return
    if (arr.length() == 0) return
    var index by remember(payload) { mutableIntStateOf(0) }
    var flipped by remember(payload) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(350), label = "flip")
    val card = arr.optJSONObject(index)
    val accent = LocalAccent.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 140.dp)
                .graphicsLayer { rotationY = rotation; cameraDistance = 12 * density }
                .clip(RoundedCornerShape(18.dp))
                .background(if (rotation <= 90f) Brush.linearGradient(listOf(accent.deep, GA.VioletDeep)) else Brush.linearGradient(listOf(GA.SurfaceHigher, GA.Surface)))
                .clickable { flipped = !flipped }
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (rotation <= 90f) card?.optString("front").orEmpty() else card?.optString("back").orEmpty(),
                color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { rotationY = if (rotation > 90f) 180f else 0f },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ Sebelumnya", color = if (index > 0) accent.main else GA.TextSecondary, fontSize = 13.sp,
                modifier = Modifier.clickable(enabled = index > 0) { index--; flipped = false }.padding(8.dp))
            Text("${index + 1} / ${arr.length()}", color = GA.TextSecondary, fontSize = 12.sp)
            Text("Berikutnya ›", color = if (index < arr.length() - 1) accent.main else GA.TextSecondary, fontSize = 13.sp,
                modifier = Modifier.clickable(enabled = index < arr.length() - 1) { index++; flipped = false }.padding(8.dp))
        }
    }
}

/** Daftar tugas interaktif (fitur 19). */
@Composable
private fun ChecklistView(msg: MessageEntity, payload: JSONObject?, actions: MessageActions) {
    val items = payload?.optJSONArray("items") ?: return
    var done = 0
    for (i in 0 until items.length()) if (items.optJSONObject(i)?.optBoolean("d") == true) done++
    Column {
        Text(msg.text, color = GA.TextPrimary, fontWeight = FontWeight.SemiBold)
        Text("$done dari ${items.length()} selesai", color = GA.TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: continue
            val d = it.optBoolean("d")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { actions.onToggleChecklist(msg, i) }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (d) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null,
                    tint = if (d) GA.Green else GA.TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(it.optString("t"), color = if (d) GA.TextSecondary else GA.TextPrimary, fontSize = 14.sp,
                    textDecoration = if (d) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
            }
        }
    }
}

/** Perbandingan jawaban Cepat vs Mendalam berdampingan (fitur 20). */
@Composable
private fun CompareView(payload: JSONObject?, actions: MessageActions) {
    val fast = payload?.optJSONObject("fast")
    val deep = payload?.optJSONObject("deep")
    if (fast == null && deep == null) {
        Text("_(Perbandingan tidak tersedia)_", color = GA.TextSecondary)
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompareColumn("⚡ Cepat (Thinking OFF)", fast, actions)
        CompareColumn("🧠 Mendalam (Thinking ON)", deep, actions)
    }
}

@Composable
private fun CompareColumn(title: String, o: JSONObject?, actions: MessageActions) {
    Column(
        Modifier.width(260.dp).clip(RoundedCornerShape(14.dp)).background(GA.Black)
            .border(1.dp, GA.Outline, RoundedCornerShape(14.dp)).padding(10.dp)
    ) {
        Text(title, color = LocalAccent.current.main, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        val tps = o?.optDouble("tps", 0.0) ?: 0.0
        if (tps > 0) Text("${String.format(Locale("id", "ID"), "%.1f", tps)} token/detik", color = GA.TextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        val thought = o?.optString("thought").orEmpty()
        if (thought.isNotBlank()) ThoughtSection(thought)
        MarkdownText(o?.optString("text").orEmpty().ifBlank { "_(kosong)_" }, onWordLongPress = actions.onWord, onCopyCode = actions.onCopyCode)
    }
}

