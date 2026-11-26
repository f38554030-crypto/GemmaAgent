package com.gemmaagent.app.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import com.gemmaagent.app.data.db.Kinds
import com.gemmaagent.app.data.db.MessageEntity
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.LocalAccent
import com.gemmaagent.app.ui.theme.LocalFontScale
import com.gemmaagent.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    val onPreviewCode: (String, String) -> Unit = { _, _ -> },
    val onSelectVersion: (MessageEntity, Int) -> Unit = { _, _ -> },
    val onCompareVersions: (String, String) -> Unit = { _, _ -> },
)

@Composable
internal fun ThoughtSection(thought: String) {
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

/** Kuis pilihan ganda interaktif (fitur 18). */
@Composable
internal fun QuizView(payload: JSONObject?) {
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
                Text("${qi + 1}. ${q.optString("q")}", color = GA.Strong, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
internal fun FlashcardsView(payload: JSONObject?) {
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
                .background(if (rotation <= 90f) GA.Sparkle else Brush.linearGradient(listOf(GA.SurfaceHigher, GA.Surface)))
                .clickable { flipped = !flipped }
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (rotation <= 90f) card?.optString("front").orEmpty() else card?.optString("back").orEmpty(),
                color = if (rotation <= 90f) Color.White else GA.Strong, fontSize = 16.sp, textAlign = TextAlign.Center,
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
internal fun ChecklistView(msg: MessageEntity, payload: JSONObject?, actions: MessageActions) {
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
internal fun CompareView(payload: JSONObject?, actions: MessageActions) {
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
internal fun CompareColumn(title: String, o: JSONObject?, actions: MessageActions) {
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



/** Indikator langkah pencarian web yang bisa diketuk (fitur 14). */
@Composable
internal fun SearchSteps(steps: List<String>, query: String?, liveOpen: Boolean) {
    var open by remember { mutableStateOf(liveOpen) }
    val accent = LocalAccent.current.main
    Column(Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(GA.Surface).border(1.dp, GA.Outline, RoundedCornerShape(12.dp))) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Public, null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (query != null) "Mencari di web: \"${query.take(40)}\"" else steps.last(),
                color = GA.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(if (open) "▴" else "▾", color = GA.TextSecondary, fontSize = 12.sp)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 30.dp, end = 10.dp, bottom = 8.dp)) {
                steps.forEach { Text("• $it", color = GA.TextSecondary, fontSize = 11.sp) }
            }
        }
    }
}


// =====================================================================
// BARIS-BARIS CHAT (tampilan editorial ala Gemini / Perplexity)
// =====================================================================

/** Pesan pengguna: gelembung membulat modern di sisi kanan. */
@Composable
fun UserRowView(row: UserRow, actions: MessageActions, enabled: Boolean) {
    val msg = row.msg
    val scale = LocalFontScale.current
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(start = 48.dp, top = 18.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        msg.imagePath?.let { ImageThumb(it) }
        if (msg.attachmentName != null && msg.imagePath == null) {
            AttachmentChip(msg.attachmentName)
            Spacer(Modifier.height(4.dp))
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 8.dp))
                .background(GA.UserBubble)
                .clickable { menu = true }
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Text(msg.text, color = GA.OnUserBubble, fontSize = (15.5f * scale).sp, lineHeight = (22 * scale).sp)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Salin") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    onClick = { menu = false; actions.onCopy(msg.text) })
                DropdownMenuItem(text = { Text("Edit & kirim ulang") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    enabled = enabled, onClick = { menu = false; actions.onEdit(msg) })
                DropdownMenuItem(text = { Text("Simpan ke Memori AI") }, leadingIcon = { Icon(Icons.Filled.Psychology, null) },
                    onClick = { menu = false; actions.onRemember(msg) })
            }
        }
    }
}

/** Thumbnail gambar didekode di Dispatchers.IO (bukan di thread UI). */
@Composable
private fun ImageThumb(path: String) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }.getOrNull()
        }
    }
    Crossfade(targetState = bmp, animationSpec = tween(220), label = "thumb") { b ->
        if (b != null) {
            Image(
                b, contentDescription = "Gambar terlampir", contentScale = ContentScale.Crop,
                modifier = Modifier.padding(bottom = 6.dp).size(176.dp).clip(RoundedCornerShape(18.dp)),
            )
        } else {
            Box(Modifier.padding(bottom = 6.dp).size(176.dp).clip(RoundedCornerShape(18.dp)).background(GA.SurfaceHigh))
        }
    }
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

/** Avatar kilau (sparkle) kecil untuk jawaban AI. */
@Composable
fun SparkleAvatar(size: androidx.compose.ui.unit.Dp = 26.dp, spinning: Boolean = false) {
    val rotation = if (spinning) {
        val t = rememberInfiniteTransition(label = "spark")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(2400)), label = "rot").value
    } else 0f
    Box(
        Modifier.size(size).graphicsLayer { rotationZ = rotation }.clip(CircleShape).background(GA.Sparkle),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(size * 0.58f)) }
}

/** Header jawaban AI: avatar, karusel sumber web (Perplexity), langkah pencarian, proses berpikir. */
@Composable
fun AiHeaderView(row: AiHeaderRow) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SparkleAvatar(spinning = row.live)
            Spacer(Modifier.width(10.dp))
            Crossfade(targetState = if (row.live) row.phase else "", animationSpec = tween(200), label = "phase") { p ->
                if (p.isNotBlank()) ShimmerText(p) else Spacer(Modifier.height(1.dp))
            }
        }
        AnimatedVisibility(visible = row.sources.isNotEmpty(), enter = fadeIn(tween(250)) + expandVertically(tween(250))) {
            SourcesCarousel(row.sources, row.fromCache)
        }
        if (row.steps.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            SearchSteps(row.steps, row.query, liveOpen = false)
        }
        if (row.thought.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            if (row.live) {
                Text("🧠 " + row.thought.takeLast(280), color = GA.Violet.copy(alpha = 0.8f), fontSize = 12.sp,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
            } else ThoughtSection(row.thought)
        }
    }
}

/** Teks status dengan efek kilau halus (shimmer) saat AI bekerja. */
@Composable
private fun ShimmerText(text: String) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Text(text, color = GA.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(a))
}

/** Karusel sumber web horizontal (LazyRow) di atas jawaban — gaya Perplexity. */
@Composable
private fun SourcesCarousel(sources: List<SourceItem>, fromCache: Boolean) {
    val uri = LocalUriHandler.current
    val accent = LocalAccent.current.main
    Column(Modifier.padding(top = 8.dp)) {
        Text(if (fromCache) "Sumber (arsip offline)" else "Sumber", color = GA.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
            itemsIndexed(sources, key = { i, s -> "$i${s.url}" }) { i, s ->
                Column(
                    Modifier.width(168.dp).height(92.dp).clip(RoundedCornerShape(14.dp)).background(GA.SurfaceHigh)
                        .border(1.dp, GA.Outline, RoundedCornerShape(14.dp))
                        .clickable { runCatching { uri.openUri(s.url) } }.padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(s.title, color = GA.TextPrimary, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(s.host, color = GA.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Satu blok Markdown jawaban AI — teks menyatu dengan latar (tanpa kotak). */
@Composable
fun AiBlockView(row: AiBlockRow, actions: MessageActions) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        MarkdownBlock(row.block, GA.TextPrimary, actions.onWord, actions.onCopyCode, actions.onPreviewCode)
    }
}

/** Indikator AI sedang menulis (titik mengetik). */
@Composable
fun AiLiveView(row: AiLiveRow) {
    val accent = LocalAccent.current.main
    Column(Modifier.padding(top = 6.dp, bottom = 10.dp)) {
        row.compareFast?.let {
            Text("⚡ Mode Cepat selesai:", color = GA.TextSecondary, fontSize = 11.sp)
            Text(it.take(220) + if (it.length > 220) "…" else "", color = GA.TextSecondary, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) { TypingDots(accent, dotSize = 6.dp) }
    }
}

/** Konten khusus: kuis, flashcard, checklist, mode banding, error. */
@Composable
fun AiSpecialView(row: AiSpecialRow, actions: MessageActions) {
    val msg = row.msg
    val payload = remember(msg.payload) { runCatching { JSONObject(msg.payload) }.getOrNull() }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        when (msg.kind) {
            Kinds.QUIZ -> { Text(msg.text, color = GA.TextPrimary, fontSize = 14.sp); Spacer(Modifier.height(8.dp)); QuizView(payload) }
            Kinds.FLASHCARDS -> { Text(msg.text, color = GA.TextPrimary, fontSize = 14.sp); Spacer(Modifier.height(8.dp)); FlashcardsView(payload) }
            Kinds.CHECKLIST -> ChecklistView(msg, payload, actions)
            Kinds.COMPARE -> CompareView(payload, actions)
            Kinds.ERROR -> Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GA.Red.copy(alpha = 0.10f))
                    .border(1.dp, GA.Red.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(12.dp),
            ) {
                Icon(Icons.Filled.WarningAmber, null, tint = GA.Red, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(msg.text.removePrefix("⚠️ "), color = GA.TextPrimary, fontSize = 14.sp)
            }
            else -> MarkdownText(msg.text, onWordLongPress = actions.onWord, onCopyCode = actions.onCopyCode, onPreviewCode = actions.onPreviewCode)
        }
    }
}

/** Footer jawaban: versi, metrik, bilah aksi ikon minimalis, saran lanjutan. */
@Composable
fun AiFooterView(row: AiFooterRow, generating: Boolean, speaking: Boolean, actions: MessageActions) {
    val msg = row.msg
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        row.versions?.let { VersionsBar(msg, it, actions) }
        if (msg.kind == Kinds.ERROR) {
            if (row.isLast && !generating) Row { IconAction(Icons.Filled.Refresh, "Coba lagi") { actions.onRegenerate(msg) } }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.Filled.ContentCopy, "Salin") { actions.onCopy(msg.text) }
            IconAction(if (speaking) Icons.Filled.StopCircle else Icons.AutoMirrored.Filled.VolumeUp, if (speaking) "Hentikan suara" else "Bacakan",
                tint = if (speaking) LocalAccent.current.main else null) { actions.onSpeak(msg) }
            if (row.isLast && msg.kind != Kinds.CHECKLIST) {
                IconAction(Icons.Filled.Refresh, "Regenerasi", enabled = !generating) { actions.onRegenerate(msg) }
            }
            IconAction(if (msg.starred) Icons.Filled.Star else Icons.Filled.StarBorder, "Bintang", tint = if (msg.starred) GA.Amber else null) { actions.onStar(msg) }
            IconAction(Icons.Filled.IosShare, "Bagikan sebagai kartu") { actions.onShareCard(msg) }
            var more by remember { mutableStateOf(false) }
            Box {
                IconAction(Icons.Filled.MoreHoriz, "Lainnya") { more = true }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text("📒 Simpan ke Catatan") }, onClick = { more = false; actions.onSaveNote(msg) })
                    DropdownMenuItem(text = { Text("✅ Jadikan checklist") }, onClick = { more = false; actions.onChecklist(msg) })
                    DropdownMenuItem(text = { Text("📝 Buat kuis") }, enabled = !generating, onClick = { more = false; actions.onQuiz(msg) })
                    DropdownMenuItem(text = { Text("🃏 Buat flashcard") }, enabled = !generating, onClick = { more = false; actions.onFlashcards(msg) })
                }
            }
            Spacer(Modifier.weight(1f))
            MetaInfo(msg)
        }
        AnimatedVisibility(visible = row.isLast && !generating && row.followUps.isNotEmpty(), enter = fadeIn(tween(300)), exit = fadeOut(tween(150))) {
            FollowUpChips(row.followUps, actions.onFollowUp)
        }
    }
}

@Composable
private fun MetaInfo(msg: MessageEntity) {
    val parts = buildList {
        if (msg.tokensPerSec > 0) add(String.format(Locale.US, "%.1f tok/s", msg.tokensPerSec))
        if (msg.online) add("Online")
        if (msg.kind == Kinds.CALC) add("Kalkulator lokal")
    }
    if (parts.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (msg.tokensPerSec > 0) Icon(Icons.Filled.Speed, null, tint = GA.TextSecondary.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
        if (msg.online) Icon(Icons.Filled.Public, null, tint = LocalAccent.current.main.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(parts.joinToString(" • "), color = GA.TextSecondary.copy(alpha = 0.8f), fontSize = 10.5.sp)
    }
}

@Composable
private fun IconAction(icon: ImageVector, desc: String, tint: Color? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = (tint ?: GA.TextSecondary).copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun VersionsBar(msg: MessageEntity, v: VersionInfo, actions: MessageActions) {
    val accent = LocalAccent.current.main
    Row(Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = if (v.active > 0) accent else GA.TextSecondary, fontSize = 18.sp,
            modifier = Modifier.clip(CircleShape).clickable(enabled = v.active > 0) { actions.onSelectVersion(msg, v.active - 1) }.padding(horizontal = 10.dp))
        Text("Versi ${v.active + 1}/${v.count}", color = GA.TextSecondary, fontSize = 12.sp)
        Text("›", color = if (v.active < v.count - 1) accent else GA.TextSecondary, fontSize = 18.sp,
            modifier = Modifier.clip(CircleShape).clickable(enabled = v.active < v.count - 1) { actions.onSelectVersion(msg, v.active + 1) }.padding(horizontal = 10.dp))
        Text("Bandingkan", color = accent, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { actions.onCompareVersions(v.prevText, msg.text) }.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun FollowUpChips(items: List<String>, onClick: (String) -> Unit) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { q ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).border(1.dp, GA.Outline, RoundedCornerShape(50))
                    .clickable { onClick(q) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("↳ ", color = LocalAccent.current.main, fontSize = 13.sp)
                Text(q, color = GA.TextPrimary, fontSize = 13.sp)
            }
        }
    }
}
