package com.gemmaagent.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object Notifications {
    const val CHANNEL_SERVICE = "agent_service"
    const val SERVICE_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Agen AI aktif", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notifikasi tetap agar agen tidak dimatikan sistem MIUI"
                setShowBadge(false)
            }
        )
    }
}
