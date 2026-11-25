package com.gemmaagent.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Palet AMOLED: hitam pekat + abu-abu gelap, aksen biru elektrik & ungu lembut. */
object GA {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0B0B0F)
    val SurfaceHigh = Color(0xFF15151C)
    val SurfaceHigher = Color(0xFF1E1E27)
    val Outline = Color(0xFF2A2A36)
    val Blue = Color(0xFF3D8BFF)
    val BlueDeep = Color(0xFF1F5FD6)
    val Violet = Color(0xFFB69CFF)
    val VioletDeep = Color(0xFF6E56CF)
    val Green = Color(0xFF3DDC97)
    val Red = Color(0xFFFF4D5E)
    val Amber = Color(0xFFFFC24D)
    val TextPrimary = Color(0xFFECECF1)
    val TextSecondary = Color(0xFF9A9AAD)
}

private val AmoledColors = darkColorScheme(
    primary = GA.Blue,
    onPrimary = Color.White,
    primaryContainer = GA.BlueDeep,
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
    surfaceContainerHighest = Color(0xFF262631),
    outline = GA.Outline,
    outlineVariant = Color(0xFF22222C),
    error = GA.Red,
    onError = Color.White,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun GemmaAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledColors,
        typography = Typography(),
        shapes = AppShapes,
        content = content,
    )
}
