package com.gemmaagent.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gemmaagent.app.GemmaAgentApp
import com.gemmaagent.app.MainActivity
import com.gemmaagent.app.R
import com.gemmaagent.app.llm.LlmEngine
import com.gemmaagent.app.util.Notifications
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Foreground Service dengan notifikasi aktif, supaya proses AI dan agen
 * tidak dimatikan oleh sistem Xiaomi/MIUI. Service ini juga memegang
 * tombol melayang (floating pill) STOP AGEN.
 */
class AgentForegroundService : LifecycleService() {

    private var overlay: OverlayManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        startAsForeground(buildNotification("Menyiapkan agen…"))

        val app = GemmaAgentApp.instance
        app.feedback.warmUp()
        app.llm.loadAsync()

        if (Settings.canDrawOverlays(this)) {
            overlay = OverlayManager(this, app.agent, app.llm).also { it.show() }
        }

        // Perbarui teks notifikasi mengikuti status agen.
        lifecycleScope.launch {
            combine(app.agent.statusLine, app.agent.running, app.llm.status) { line, running, status ->
                if (running) line else "${LlmEngine.MODEL_LABEL} • ${status.label}"
            }.distinctUntilChanged().debounce(300).collect { text ->
                updateNotification(text)
            }
        }
        // Jaga CPU tetap hidup selama agen bekerja.
        lifecycleScope.launch {
            app.agent.running.collect { running -> if (running) acquireWakeLock() else releaseWakeLock() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP_AGENT -> GemmaAgentApp.instance.agent.stop()
            ACTION_SHUTDOWN -> {
                GemmaAgentApp.instance.agent.stop()
                GemmaAgentApp.instance.llm.unloadAsync()
                stopSelf()
            }
            else -> {
                if (overlay == null && Settings.canDrawOverlays(this)) {
                    overlay = OverlayManager(this, GemmaAgentApp.instance.agent, GemmaAgentApp.instance.llm).also { it.show() }
                }
                GemmaAgentApp.instance.llm.loadAsync()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlay?.hide()
        overlay = null
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startAsForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, Notifications.SERVICE_NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AgentForegroundService::class.java).setAction(ACTION_STOP_AGENT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val shutdown = PendingIntent.getService(
            this, 2,
            Intent(this, AgentForegroundService::class.java).setAction(ACTION_SHUTDOWN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle("Gemma Agent aktif (offline)")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .addAction(0, "STOP AGEN", stop)
            .addAction(0, "Matikan", shutdown)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(Notifications.SERVICE_NOTIFICATION_ID, buildNotification(text))
    }

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GemmaAgent:agent").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        const val ACTION_STOP_AGENT = "com.gemmaagent.app.STOP_AGENT"
        const val ACTION_SHUTDOWN = "com.gemmaagent.app.SHUTDOWN"

        fun start(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stopAgent(context: Context) {
            GemmaAgentApp.instance.agent.stop()
        }
    }
}
