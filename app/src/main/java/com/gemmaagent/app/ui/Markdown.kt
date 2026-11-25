package com.gemmaagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.LocalAccent
import com.gemmaagent.app.ui.theme.LocalFontScale

/** Blok-blok Markdown yang didukung (fitur 44). */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Task(val done: Boolean, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {
    fun parse(src: String): List<MdBlock> {
        val lines = src.replace("\r", "").split("\n")
        val out = mutableListOf<MdBlock>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
            para.clear()
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            when {
                t.startsWith("```") -> {
                    flush()
                    val lang = t.removePrefix("```").trim()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        code.append(lines[i]).append('\n')
                        i++
                    }
                    out += MdBlock.Code(lang, code.toString().trimEnd())
                }
                t.isEmpty() -> flush()
                Regex("^#{1,6}\\s").containsMatchIn(t) -> {
                    flush()
                    val level = t.takeWhile { it == '#' }.length
                    out += MdBlock.Heading(level, t.drop(level).trim())
                }
                Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(t) -> { flush(); out += MdBlock.Rule }
                Regex("^[-*•]\\s+\\[[ xX]]\\s+").containsMatchIn(t) -> {
                    flush()
                    val done = t.contains("[x]") || t.contains("[X]")
                    out += MdBlock.Task(done, t.replace(Regex("^[-*•]\\s+\\[[ xX]]\\s+"), ""))
                }
                Regex("^[-*•+]\\s+").containsMatchIn(t) -> {
                    flush()
                    val indent = (line.length - line.trimStart().length) / 2
                    out += MdBlock.ListItem("•", t.replace(Regex("^[-*•+]\\s+"), ""), indent.coerceAtMost(3))
                }
                Regex("^\\d+[.)]\\s+").containsMatchIn(t) -> {
                    flush()
                    val num = t.takeWhile { it.isDigit() }
                    val indent = (line.length - line.trimStart().length) / 2
                    out += MdBlock.ListItem("$num.", t.replace(Regex("^\\d+[.)]\\s+"), ""), indent.coerceAtMost(3))
                }
                t.startsWith(">") -> {
                    flush()
                    out += MdBlock.Quote(t.trimStart('>').trim())
                }
                t.startsWith("|") && t.count { it == '|' } >= 2 -> {
                    flush()
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val row = lines[i].trim().trim('|').split("|").map { it.trim() }
                        if (!row.all { Regex("^:?-{2,}:?$").matches(it) }) rows += row
                        i++
                    }
                    i--
                    if (rows.isNotEmpty()) out += MdBlock.Table(rows)
                }
                else -> para.append(t).append(' ')
            }
            i++
        }
        flush()
        return out
    }

    /** Teks inline: **tebal**, *miring*, `kode`, [teks](url), [1] sitasi. */
    fun inline(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
        var i = 0
        val s = text
        while (i < s.length) {
            when {
                s.startsWith("**", i) && s.indexOf("**", i + 2) > i + 2 -> {
                    val end = s.indexOf("**", i + 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(s.substring(i + 2, end)) }
                    i = end + 2
                }
                (s[i] == '*' || s[i] == '_') && i + 1 < s.length && s[i + 1] != ' ' &&
                    s.indexOf(s[i], i + 1).let { it > i + 1 } && (i == 0 || !s[i - 1].isLetterOrDigit()) -> {
                    val end = s.indexOf(s[i], i + 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }
                    i = end + 1
                }
                s[i] == '`' && s.indexOf('`', i + 1) > i -> {
                    val end = s.indexOf('`', i + 1)
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = GA.SurfaceHigher, color = accent)) {
                        append(" ${s.substring(i + 1, end)} ")
                    }
                    i = end + 1
                }
                s[i] == '[' && Regex("^\\[([^\\]]+)]\\((https?://[^)]+)\\)").find(s.substring(i)) != null -> {
                    val m = Regex("^\\[([^\\]]+)]\\((https?://[^)]+)\\)").find(s.substring(i))!!
                    pushStringAnnotation("URL", m.groupValues[2])
                    withStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)) { append(m.groupValues[1]) }
                    pop()
                    i += m.value.length
                }
                s[i] == '[' && Regex("^\\[\\d{1,2}]").find(s.substring(i)) != null -> {
                    val m = Regex("^\\[\\d{1,2}]").find(s.substring(i))!!
                    withStyle(SpanStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) { append(m.value) }
                    i += m.value.length
                }
                else -> {
                    append(s[i])
                    i++
                }
            }
        }
    }
}

/**
 * Perender Markdown. [onWordLongPress] dipanggil saat pengguna menahan sebuah kata
 * (untuk Kamus & Penjelas Kata Kontekstual).
 */
@Composable
fun MarkdownText(
    markdown: String,
    color: Color = GA.TextPrimary,
    onWordLongPress: ((word: String, sentence: String) -> Unit)? = null,
    onCopyCode: (String) -> Unit = {},
) {
    val accent = LocalAccent.current.main
    val scale = LocalFontScale.current
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val base = (15 * scale).sp
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in blocks) {
            when (b) {
                is MdBlock.Heading -> WordText(
                    MarkdownParser.inline(b.text, accent),
                    color = Color.White,
                    size = when (b.level) { 1 -> (21 * scale).sp; 2 -> (19 * scale).sp; else -> (17 * scale).sp },
                    weight = FontWeight.Bold,
                    onWordLongPress = onWordLongPress,
                )
                is MdBlock.Paragraph -> WordText(MarkdownParser.inline(b.text, accent), color, base, onWordLongPress = onWordLongPress)
                is MdBlock.ListItem -> Row(Modifier.padding(start = (b.indent * 14).dp)) {
                    Text(b.marker, color = accent, fontSize = base, modifier = Modifier.widthIn(min = 18.dp))
                    Spacer(Modifier.width(4.dp))
                    WordText(MarkdownParser.inline(b.text, accent), color, base, onWordLongPress = onWordLongPress)
                }
                is MdBlock.Task -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (b.done) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = null, tint = if (b.done) GA.Green else GA.TextSecondary, modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    WordText(MarkdownParser.inline(b.text, accent), color, base, onWordLongPress = onWordLongPress)
                }
                is MdBlock.Quote -> Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(GA.SurfaceHigher.copy(alpha = 0.6f)).padding(end = 8.dp)
                ) {
                    Box(Modifier.width(3.dp).height(22.dp).background(accent))
                    Spacer(Modifier.width(8.dp))
                    WordText(MarkdownParser.inline(b.text, accent), GA.TextSecondary, base, italic = true, onWordLongPress = onWordLongPress)
                }
                is MdBlock.Code -> CodeBlock(b.lang, b.code, scale, onCopyCode)
                is MdBlock.Table -> TableBlock(b.rows, accent, scale)
                MdBlock.Rule -> HorizontalDivider(color = GA.Outline)
            }
        }
    }
}

@Composable
private fun WordText(
    text: AnnotatedString,
    color: Color,
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    italic: Boolean = false,
    onWordLongPress: ((String, String) -> Unit)?,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val uri = LocalUriHandler.current
    Text(
        text = text,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        lineHeight = size * 1.4f,
        onTextLayout = { layout = it },
        modifier = Modifier.pointerInput(text) {
            detectTapGestures(
                onTap = { pos ->
                    val l = layout ?: return@detectTapGestures
                    val off = l.getOffsetForPosition(pos)
                    text.getStringAnnotations("URL", off, off).firstOrNull()?.let { runCatching { uri.openUri(it.item) } }
                },
                onLongPress = { pos ->
                    val l = layout ?: return@detectTapGestures
                    val cb = onWordLongPress ?: return@detectTapGestures
                    val off = l.getOffsetForPosition(pos).coerceIn(0, (text.length - 1).coerceAtLeast(0))
                    val str = text.text
                    if (str.isEmpty()) return@detectTapGestures
                    var s = off
                    var e = off
                    while (s > 0 && str[s - 1].isLetterOrDigit()) s--
                    while (e < str.length && str[e].isLetterOrDigit()) e++
                    val word = str.substring(s, e)
                    if (word.length >= 2) {
                        val sentStart = str.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), s).let { if (it < 0) 0 else it + 1 }
                        val sentEnd = str.indexOfAny(charArrayOf('.', '!', '?', '\n'), e).let { if (it < 0) str.length else it }
                        cb(word, str.substring(sentStart, sentEnd).trim())
                    }
                },
            )
        },
    )
}

@Composable
private fun CodeBlock(lang: String, code: String, scale: Float, onCopy: (String) -> Unit) {
    val accent = LocalAccent.current.main
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A0A0D))
            .border(1.dp, GA.Outline, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().background(GA.SurfaceHigh).padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(lang.ifBlank { "kode" }, color = GA.TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { onCopy(code) }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy Code", color = accent, fontSize = 11.sp)
            }
        }
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)) {
            Text(
                SyntaxHighlighter.highlight(code, accent),
                fontFamily = FontFamily.Monospace,
                fontSize = (12.5f * scale).sp,
                color = Color(0xFFD6D6E0),
                softWrap = false,
            )
        }
    }
}

@Composable
private fun TableBlock(rows: List<List<String>>, accent: Color, scale: Float) {
    val cols = rows.maxOf { it.size }
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, GA.Outline, RoundedCornerShape(10.dp))
            .horizontalScroll(rememberScrollState())
    ) {
        Column {
            rows.forEachIndexed { r, row ->
                Row(Modifier.background(if (r == 0) GA.SurfaceHigher else if (r % 2 == 0) GA.Surface else Color.Transparent)) {
                    for (c in 0 until cols) {
                        Text(
                            MarkdownParser.inline(row.getOrElse(c) { "" }, accent),
                            color = if (r == 0) Color.White else GA.TextPrimary,
                            fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = (13 * scale).sp,
                            modifier = Modifier.width(130.dp).padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Pewarnaan sintaks sederhana: kata kunci, string, komentar, angka. */
object SyntaxHighlighter {
    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "if", "else", "when", "for", "while", "return", "import", "package",
        "def", "self", "None", "True", "False", "in", "is", "not", "and", "or", "from", "as", "with", "try", "except",
        "catch", "finally", "function", "const", "let", "new", "this", "public", "private", "static", "void", "int",
        "string", "bool", "true", "false", "null", "async", "await", "lambda", "elif", "print", "echo", "select",
        "insert", "update", "delete", "where", "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "interface",
        "enum", "struct", "override", "suspend", "data", "export", "default", "switch", "case", "break", "continue",
    )

    fun highlight(code: String, accent: Color): AnnotatedString = buildAnnotatedString {
        val strColor = Color(0xFF9CDC8F)
        val numColor = Color(0xFFF7B267)
        val comColor = Color(0xFF6B6B7B)
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                (c == '/' && code.startsWith("//", i)) || c == '#' -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    withStyle(SpanStyle(color = comColor, fontStyle = FontStyle.Italic)) { append(code.substring(i, end)) }
                    i = end
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < code.length && code[j] != c && code[j] != '\n') { if (code[j] == '\\') j++; j++ }
                    val end = (j + 1).coerceAtMost(code.length)
                    withStyle(SpanStyle(color = strColor)) { append(code.substring(i, end)) }
                    i = end
                }
                c.isDigit() -> {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '.')) j++
                    withStyle(SpanStyle(color = numColor)) { append(code.substring(i, j)) }
                    i = j
                }
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    val w = code.substring(i, j)
                    if (w in keywords) withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(w) } else append(w)
                    i = j
                }
                else -> { append(c); i++ }
            }
        }
    }
}
