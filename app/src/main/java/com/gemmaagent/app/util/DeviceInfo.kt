package com.gemmaagent.app.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceInfo {

    /** Info singkat HP untuk dimasukkan ke prompt AI (baterai, Wi-Fi, jam). */
    fun summary(context: Context): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        @Suppress("DEPRECATION")
        val wifiOn = runCatching {
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
        }.getOrDefault(false)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val mobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val wifiText = when {
            wifiConnected -> "Wi-Fi aktif & tersambung"
            wifiOn -> "Wi-Fi aktif tapi belum tersambung"
            else -> "Wi-Fi mati"
        }
        val time = SimpleDateFormat("HH:mm, EEEE d MMM yyyy", Locale("id", "ID")).format(Date())
        return "Baterai $level%${if (charging) " (sedang diisi)" else ""}; $wifiText" +
            (if (mobile) "; data seluler aktif" else "") + "; waktu $time"
    }

    /** Sisa RAM yang tersedia dalam MB (untuk indikator di Top App Bar). */
    fun availableRamMb(context: Context): Pair<Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return (mi.availMem / (1024 * 1024)) to (mi.totalMem / (1024 * 1024))
    }
}
