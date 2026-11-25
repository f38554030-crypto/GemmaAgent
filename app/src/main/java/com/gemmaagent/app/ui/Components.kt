package com.gemmaagent.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.StartOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gemmaagent.app.agent.LogKind
import com.gemmaagent.app.ui.theme.GA

/** Titik berkedip (animasi "AI sedang berpikir"). */
@Composable
fun BlinkingDot(active: Boolean, color: Color = GA.Blue, size: Dp = 10.dp) {
    val transition = rememberInfiniteTransition(label = "blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "blinkAlpha",
    )
    Box(
        Modifier
            .size(size)
            .alpha(if (active) alpha else 1f)
            .clip(CircleShape)
            .background(if (active) color else GA.Green)
    )
}

/** Tiga titik mengetik yang memantul (typing indicator). */
@Composable
fun TypingDots(color: Color = GA.Violet, dotSize: Dp = 7.dp) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 150),
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        translationY = -offset * 6.dp.toPx()
                        alpha = 0.4f + 0.6f * offset
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

fun logColor(kind: LogKind): Color = when (kind) {
    LogKind.INFO -> GA.TextSecondary
    LogKind.THINK -> GA.Violet
    LogKind.ACTION -> GA.Blue
    LogKind.WARN -> GA.Amber
    LogKind.DONE -> GA.Green
    LogKind.ERROR -> GA.Red
}
