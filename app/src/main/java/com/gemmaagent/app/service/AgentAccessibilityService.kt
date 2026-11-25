package com.gemmaagent.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Layanan Aksesibilitas: "mata dan tangan" agen.
 * - Membaca teks yang tampil di layar.
 * - Menjalankan aksi: click_text, type_text, swipe, open_app, back, home.
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastForegroundPackage = pkg
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    // ------------------------------------------------------------------
    // MEMBACA LAYAR
    // ------------------------------------------------------------------

    data class ScreenElement(val text: String, val clickable: Boolean, val editable: Boolean)

    data class ScreenSnapshot(val packageName: String, val elements: List<ScreenElement>) {
        /** Format ringkas untuk prompt (dibatasi panjangnya agar muat di 1536 token). */
        fun toPrompt(maxChars: Int = 1300): String {
            val sb = StringBuilder()
            for (e in elements) {
                val tag = when {
                    e.editable -> "[kolom-ketik] "
                    e.clickable -> "[tombol] "
                    else -> ""
                }
                val line = "- $tag${e.text}\n"
                if (sb.length + line.length > maxChars) {
                    sb.append("- …(dipotong)\n")
                    break
                }
                sb.append(line)
            }
            if (sb.isEmpty()) sb.append("(tidak ada teks terbaca)\n")
            return sb.toString()
        }

        fun signature(): Int = (packageName + elements.joinToString("|") { it.text }).hashCode()
    }

    fun readScreen(): ScreenSnapshot {
        val root = rootInActiveWindow ?: return ScreenSnapshot(lastForegroundPackage ?: "?", emptyList())
        val pkg = root.packageName?.toString() ?: lastForegroundPackage ?: "?"
        val out = LinkedHashMap<String, ScreenElement>()
        collect(root, out, 0)
        return ScreenSnapshot(pkg, out.values.toList())
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableMap<String, ScreenElement>, depth: Int) {
        if (node == null || depth > 40 || out.size >= 80) return
        if (node.isVisibleToUser) {
            val raw = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                ?: if (node.isEditable) (node.hintText?.toString() ?: "kolom teks") else null
            if (raw != null) {
                val text = raw.replace('\n', ' ').trim().take(80)
                val clickable = node.isClickable || hasClickableParent(node)
                val key = text.lowercase()
                if (!out.containsKey(key)) out[key] = ScreenElement(text, clickable, node.isEditable)
            }
        }
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), out, depth + 1)
        }
    }

    private fun hasClickableParent(node: AccessibilityNodeInfo): Boolean {
        var p = node.parent
        var level = 0
        while (p != null && level < 4) {
            if (p.isClickable) return true
            p = p.parent
            level++
        }
        return false
    }

    // ------------------------------------------------------------------
    // AKSI
    // ------------------------------------------------------------------

    /** Mencari elemen yang teksnya cocok (tanpa mengeklik). Dipakai juga oleh Safety Guard. */
    fun findNodeText(target: String): String? = findNode(target)?.let { n ->
        (n.text ?: n.contentDescription)?.toString()
    }

    private fun findNode(target: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val wanted = target.trim().lowercase()
        if (wanted.isEmpty()) return null
        var exact: AccessibilityNodeInfo? = null
        var partial: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || exact != null) return
            if (n.isVisibleToUser) {
                val label = (n.text ?: n.contentDescription)?.toString()?.trim()?.lowercase()
                if (label != null) {
                    if (label == wanted) exact = n
                    else if (partial == null && label.contains(wanted)) partial = n
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return exact ?: partial
    }

    suspend fun clickText(target: String): Boolean {
        val node = findNode(target) ?: return false
        var n: AccessibilityNodeInfo? = node
        var level = 0
        while (n != null && level < 6) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            level++
        }
        // Cadangan: ketuk koordinat tengah elemen.
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return false
        return tap(r.exactCenterX(), r.exactCenterY())
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: findFirstEditable(root, 0)
            ?: return false
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findFirstEditable(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (n == null || depth > 40) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            findFirstEditable(n.getChild(i), depth + 1)?.let { return it }
        }
        return null
    }

    suspend fun swipe(direction: String): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val (x1, y1, x2, y2) = when (direction.lowercase()) {
            "up", "atas" -> listOf(w / 2, h * 0.75f, w / 2, h * 0.25f)
            "down", "bawah" -> listOf(w / 2, h * 0.25f, w / 2, h * 0.75f)
            "left", "kiri" -> listOf(w * 0.85f, h / 2, w * 0.15f, h / 2)
            "right", "kanan" -> listOf(w * 0.15f, h / 2, w * 0.85f, h / 2)
            else -> return false
        }
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return gesture(path, 350)
    }

    private suspend fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return gesture(path, 60)
    }

    private suspend fun gesture(path: Path, durationMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val ok = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }

    /** Membuka aplikasi berdasarkan nama (contoh: "WhatsApp", "Pengaturan"). */
    fun openApp(name: String): String? {
        val pm = packageManager
        val wanted = name.trim().lowercase()
        if (wanted.isEmpty()) return null
        val aliases = mapOf(
            "pengaturan" to listOf("settings", "setelan", "pengaturan"),
            "settings" to listOf("settings", "setelan", "pengaturan"),
            "setelan" to listOf("settings", "setelan", "pengaturan"),
            "kamera" to listOf("camera", "kamera"),
            "galeri" to listOf("gallery", "galeri", "photos", "foto"),
            "wa" to listOf("whatsapp"),
        )
        val candidates = aliases[wanted] ?: listOf(wanted)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val match = apps.firstOrNull { ri ->
            val label = ri.loadLabel(pm).toString().lowercase()
            candidates.any { label == it }
        } ?: apps.firstOrNull { ri ->
            val label = ri.loadLabel(pm).toString().lowercase()
            val pkg = ri.activityInfo.packageName.lowercase()
            candidates.any { label.contains(it) || pkg.contains(it.replace(" ", "")) }
        }
        if (match == null) {
            if (candidates.contains("settings")) {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Pengaturan"
            }
            return null
        }
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(match.activityInfo.packageName, match.activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        startActivity(intent)
        return match.loadLabel(pm).toString()
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        @Volatile
        var lastForegroundPackage: String? = null
            private set

        /** Mengecek apakah layanan ini sudah diaktifkan di Pengaturan > Aksesibilitas. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, AgentAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it)?.let { c -> c == expected } == true
            }
        }
    }
}
