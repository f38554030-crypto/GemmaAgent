package com.gemmaagent.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gemmaagent.app.data.Accent
import com.gemmaagent.app.data.ThemeMode

/** Palet warna aktif (gelap/terang). */
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val card: Color,
    val cardHigh: Color,
    val outline: Color,
    val userBubble: Color,
    val onUserBubble: Color,
    val text: Color,
    val textSecondary: Color,
    val strong: Color,
    val codeBg: Color,
)

/** Gemini Dark: Deep Obsidian #0E0E10, Elevated Slate #1A1B1E, Soft Indigo-Blue #242938. */
val GeminiDark = Palette(
    dark = true,
    bg = Color(0xFF0E0E10),
    surface = Color(0xFF141518),
    card = Color(0xFF1A1B1E),
    cardHigh = Color(0xFF232429),
    outline = Color(0xFF2C2E33),
    userBubble = Color(0xFF242938),
    onUserBubble = Color(0xFFE8EAF0),
    text = Color(0xFFE3E3E8),
    textSecondary = Color(0xFF9AA0A6),
    strong = Color(0xFFFFFFFF),
    codeBg = Color(0xFF0A0A0C),
)

/** Clean Light: Pure Alabaster #F8F9FA, kartu putih #FFFFFF. */
val GeminiLight = Palette(
    dark = false,
    bg = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardHigh = Color(0xFFF0F2F5),
    outline = Color(0xFFDADCE0),
    userBubble = Color(0xFFE8F0FE),
    onUserBubble = Color(0xFF1F1F24),
    text = Color(0xFF1F1F24),
    textSecondary = Color(0xFF5F6368),
    strong = Color(0xFF000000),
    codeBg = Color(0xFFF1F3F4),
)

/**
 * Akses warna dari mana saja. Nilai dibaca dari state Compose, sehingga mengganti tema
 * (Gelap/Terang) otomatis memperbarui seluruh tampilan.
 */
object GA {
    internal var palette by mutableStateOf(GeminiDark)

    val Black: Color get() = palette.bg
    val Surface: Color get() = palette.surface
    val SurfaceHigh: Color get() = palette.card
    val SurfaceHigher: Color get() = palette.cardHigh
    val Outline: Color get() = palette.outline
    val UserBubble: Color get() = palette.userBubble
    val OnUserBubble: Color get() = palette.onUserBubble
    val TextPrimary: Color get() = palette.text
    val TextSecondary: Color get() = palette.textSecondary
    val Strong: Color get() = palette.strong
    val CodeBg: Color get() = palette.codeBg
    val IsDark: Boolean get() = palette.dark

    val Violet = Color(0xFF9B72CB)
    val VioletDeep = Color(0xFF7B57B0)
    val Rose = Color(0xFFD96570)
    val GBlue = Color(0xFF4285F4)
    val Green = Color(0xFF34A853)
    val Red = Color(0xFFEA4335)
    val Amber = Color(0xFFFBBC04)

    /** Gradasi "Sparkle" Gemini: biru → ungu → merah muda. */
    val Sparkle: Brush get() = Brush.linearGradient(listOf(GBlue, Violet, Rose))
}

data class AccentColors(val main: Color, val deep: Color, val soft: Color)

fun accentColors(a: Accent): AccentColors = when (a) {
    Accent.GEMINI -> AccentColors(Color(0xFF4285F4), Color(0xFF7B57B0), Color(0xFFAECBFA))
    Accent.BLUE -> AccentColors(Color(0xFF3D8BFF), Color(0xFF1F5FD6), Color(0xFF9CC3FF))
    Accent.EMERALD -> AccentColors(Color(0xFF2ECC8F), Color(0xFF17885C), Color(0xFF9CF0CD))
    Accent.PURPLE -> AccentColors(Color(0xFFA78BFA), Color(0xFF6E56CF), Color(0xFFD7C9FF))
    Accent.MONO -> AccentColors(Color(0xFF9AA0A6), Color(0xFF5A5A66), Color(0xFFDADCE0))
}

val LocalAccent = staticCompositionLocalOf { accentColors(Accent.GEMINI) }
val LocalFontScale = staticCompositionLocalOf { 1f }

@Composable
fun GemmaTheme(accent: Accent, mode: ThemeMode, fontScale: Float, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val p = if (dark) GeminiDark else GeminiLight
    GA.palette = p
    val ac = accentColors(accent)
    val scheme = if (dark) darkColorScheme(
        primary = ac.main, onPrimary = Color.White, primaryContainer = ac.deep, onPrimaryContainer = Color.White,
        secondary = GA.Violet, onSecondary = Color.White, tertiary = GA.Rose,
        background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
        surfaceVariant = p.card, onSurfaceVariant = p.textSecondary,
        surfaceContainerLowest = p.bg, surfaceContainerLow = p.surface, surfaceContainer = p.card,
        surfaceContainerHigh = p.cardHigh, surfaceContainerHighest = Color(0xFF2B2C31),
        outline = p.outline, outlineVariant = p.outline, error = GA.Red, onError = Color.White,
    ) else lightColorScheme(
        primary = ac.main, onPrimary = Color.White, primaryContainer = ac.soft, onPrimaryContainer = Color(0xFF0B1E3F),
        secondary = GA.Violet, onSecondary = Color.White, tertiary = GA.Rose,
        background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text,
        surfaceVariant = p.cardHigh, onSurfaceVariant = p.textSecondary,
        surfaceContainerLowest = Color.White, surfaceContainerLow = p.surface, surfaceContainer = p.card,
        surfaceContainerHigh = p.cardHigh, surfaceContainerHighest = Color(0xFFE8EAED),
        outline = p.outline, outlineVariant = p.outline, error = GA.Red, onError = Color.White,
    )
    CompositionLocalProvider(LocalAccent provides ac, LocalFontScale provides fontScale) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
            content = content,
        )
    }
}
