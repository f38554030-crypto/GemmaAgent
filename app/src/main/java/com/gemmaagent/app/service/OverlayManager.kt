package com.gemmaagent.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gemmaagent.app.GemmaAgentApp
import com.gemmaagent.app.agent.AgentController
import com.gemmaagent.app.llm.LlmEngine
import com.gemmaagent.app.llm.ModelStatus
import com.gemmaagent.app.ui.BlinkingDot
import com.gemmaagent.app.ui.logColor
import com.gemmaagent.app.ui.theme.GA
import com.gemmaagent.app.ui.theme.GemmaAgentTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** LifecycleOwner sederhana agar Jetpack Compose bisa tampil di jendela overlay. */
private class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun start() {
        controller.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

/**
 * Jendela melayang:
 * 1) Floating Pill (kapsul transparan) di pinggir layar: titik berkedip, status 1 baris, tombol merah STOP.
 *    Ketuk teks status untuk membuka mini-panel log pemikiran AI.
 * 2) Pop-up konfirmasi Safety Guard untuk aksi sensitif.
 */
class OverlayManager(
    private val context: Context,
    private val agent: AgentController,
    private val llm: LlmEngine,
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val owner = OverlayLifecycleOwner()

    private var pillView: ComposeView? = null
    private var confirmView: ComposeView? = null
    private val pillParams = baseParams().apply {
        gravity = Gravity.TOP or Gravity.START
        x = 12
        y = 260
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun baseParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    )

    private fun newComposeView(content: @Composable () -> Unit) = ComposeView(context).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setContent { GemmaAgentTheme { content() } }
    }

    fun show() {
        if (pillView != null) return
        owner.start()
        val view = newComposeView { FloatingPill() }
        runCatching { wm.addView(view, pillParams) }.onSuccess { pillView = view }

        // Tampilkan / sembunyikan pop-up konfirmasi sesuai permintaan agen.
        scope.launch {
            agent.confirm.collect { req -> if (req != null) showConfirm() else hideConfirm() }
        }
    }

    fun hide() {
        scope.cancel()
        hideConfirm()
        pillView?.let { runCatching { wm.removeView(it) } }
        pillView = null
        owner.destroy()
    }

    private fun moveBy(dx: Float, dy: Float) {
        val v = pillView ?: return
        pillParams.x = (pillParams.x + dx.toInt()).coerceAtLeast(0)
        pillParams.y = (pillParams.y + dy.toInt()).coerceAtLeast(0)
        runCatching { wm.updateViewLayout(v, pillParams) }
    }

    private fun showConfirm() {
        if (confirmView != null) return
        val params = baseParams().apply {
            gravity = Gravity.CENTER
            flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = 0.6f
        }
        val view = newComposeView { ConfirmDialog() }
        runCatching { wm.addView(view, params) }.onSuccess { confirmView = view }
    }

    private fun hideConfirm() {
        confirmView?.let { runCatching { wm.removeView(it) } }
        confirmView = null
    }

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------

    @Composable
    private fun FloatingPill() {
        val running by agent.running.collectAsState()
        val status by agent.statusLine.collectAsState()
        val modelStatus by llm.status.collectAsState()
        val log by agent.log.collectAsState()
        var expanded by remember { mutableStateOf(false) }
        val feedback = GemmaAgentApp.instance.feedback

        val line = if (running) status else "Gemma • ${modelStatus.label}"
        val thinking = running || modelStatus == ModelStatus.LOADING

        Column(
            Modifier
                .widthIn(max = 280.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        moveBy(drag.x, drag.y)
                    }
                }
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC0B0B0F))
                    .border(1.dp, if (running) GA.Blue.copy(alpha = 0.6f) else GA.Outline, RoundedCornerShape(50))
                    .padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BlinkingDot(active = thinking)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = line,
                    color = GA.TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 160.dp)
                        .clickable { expanded = !expanded },
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (running) GA.Red else GA.Red.copy(alpha = 0.35f))
                        .clickable {
                            feedback.stopHaptic()
                            agent.stop()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "STOP AGEN", tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("STOP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Mini-panel log pemikiran AI (real-time)
            AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(
                    Modifier
                        .padding(top = 6.dp)
                        .width(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE60B0B0F))
                        .border(1.dp, GA.Outline, RoundedCornerShape(16.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("Log Pemikiran AI", color = GA.Violet, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (log.isEmpty()) {
                        Text("Belum ada aktivitas.", color = GA.TextSecondary, fontSize = 11.sp)
                    }
                    log.takeLast(6).forEach { entry ->
                        Text(
                            "• ${entry.text}",
                            color = logColor(entry.kind),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ConfirmDialog() {
        val req by agent.confirm.collectAsState()
        val r = req ?: return
        val feedback = GemmaAgentApp.instance.feedback
        Column(
            Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(GA.SurfaceHigh)
                .border(1.dp, GA.Amber.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = GA.Amber, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text("Konfirmasi Aksi Sensitif", color = GA.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Agen ingin: ${r.actionLabel}\nTerdeteksi kata berisiko \"${r.riskyWord}\". Izinkan?",
                color = GA.TextSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { feedback.clickHaptic(); agent.answerConfirmation(false) },
                    modifier = Modifier.weight(1f),
                ) { Text("Tolak") }
                Button(
                    onClick = { feedback.clickHaptic(); agent.answerConfirmation(true) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = GA.Amber, contentColor = Color.Black),
                ) { Text("Izinkan", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
