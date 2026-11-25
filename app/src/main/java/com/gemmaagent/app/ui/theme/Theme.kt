package com.gemmaagent.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gemmaagent.app.data.Accent

/** Palet AMOLED: hitam pekat #000000 / #0D0D0F + aksen pilihan pengguna. */
object GA {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0D0D0F)
    val SurfaceHigh = Color(0xFF16161B)
    val SurfaceHigher = Color(0xFF1F1F26)
    val Outline = Color(0xFF2A2A34)
    val Violet = Color(0xFFB69CFF)
    val VioletDeep = Color(0xFF6E56CF)
    val Green = Color(0xFF3DDC97)
    val Red = Color(0xFFFF4D5E)
    val Amber = Color(0xFFFFC24D)
    val TextPrimary = Color(0xFFECECF1)
    val TextSecondary = Color(0xFF9A9AAD)
}

data class AccentColors(val main: Color, val deep: Color, val soft: Color)

fun accentColors(a: Accent): AccentColors = when (a) {
    Accent.BLUE -> AccentColors(Color(0xFF3D8BFF), Color(0xFF1F5FD6), Color(0xFF9CC3FF))
    Accent.EMERALD -> AccentColors(Color(0xFF2ECC8F), Color(0xFF17885C), Color(0xFF9CF0CD))
    Accent.PURPLE -> AccentColors(Color(0xFFA78BFA), Color(0xFF6E56CF), Color(0xFFD7C9FF))
    Accent.MONO -> AccentColors(Color(0xFFE6E6E6), Color(0xFF5A5A66), Color(0xFFFFFFFF))
}

val LocalAccent = staticCompositionLocalOf { accentColors(Accent.BLUE) }
val LocalFontScale = staticCompositionLocalOf { 1f }

@Composable
fun GemmaTheme(accent: Accent, fontScale: Float, content: @Composable () -> Unit) {
    val ac = accentColors(accent)
    val scheme = darkColorScheme(
        primary = ac.main,
        onPrimary = if (accent == Accent.MONO) Color.Black else Color.White,
        primaryContainer = ac.deep,
        onPrimaryContainer = Color.White,
        secondary = GA.Violet,
        onSecondary = Color(0xFF1A1033),
        secondaryContainer = Color(0xFF2A2145),
        onSecondaryContainer = GA.Violet,
        tertiary = GA.Green,
        background = GA.Black,
        onBackground = GA.TextPrimary,
        surface = GA.Black,
        onSurface = GA.TextPrimary,
        surfaceVariant = GA.SurfaceHigh,
        onSurfaceVariant = GA.TextSecondary,
        surfaceContainerLowest = GA.Black,
        surfaceContainerLow = GA.Surface,
        surfaceContainer = GA.SurfaceHigh,
        surfaceContainerHigh = GA.SurfaceHigher,
        surfaceContainerHighest = Color(0xFF282832),
        outline = GA.Outline,
        outlineVariant = Color(0xFF22222C),
        error = GA.Red,
        onError = Color.White,
    )
    CompositionLocalProvider(LocalAccent provides ac, LocalFontScale provides fontScale) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
