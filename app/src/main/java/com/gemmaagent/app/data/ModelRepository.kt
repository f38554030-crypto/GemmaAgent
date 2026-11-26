package com.gemmaagent.app.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gemmaagent.app.llm.EngineType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Status penyalinan / pengunduhan model. */
sealed interface CopyState {
    data object Idle : CopyState
    data class Copying(val percent: Int, val copiedBytes: Long, val totalBytes: Long, val label: String = "Menyalin") : CopyState
    data class Done(val message: String) : CopyState
    data class Error(val message: String) : CopyState
}

enum class ModelSource(val label: String) {
    DOWNLOAD_DIRECT("langsung dari penyimpanan (zero-copy)"),
    APP_FOLDER("folder GemmaModels"),
    INTERNAL_COPY("salinan internal lama"),
}

data class ModelFile(val path: String, val name: String, val sizeBytes: Long, val source: ModelSource) {
    val type: EngineType get() = if (name.endsWith(".gguf", true)) EngineType.GGUF else EngineType.LITERT

    val shortLabel: String
        get() {
            val n = name.substringBeforeLast('.')
            val gemma = Regex("gemma[-_ ]?(\\d+n?)", RegexOption.IGNORE_CASE).find(n)
            val quant = Regex("(I?Q\\d[_A-Z0-9]*|F16|BF16|Q8_0)", RegexOption.IGNORE_CASE).find(n)?.value?.uppercase()
            val size = Regex("\\b(E\\d+B|\\d+B)\\b", RegexOption.IGNORE_CASE).find(n.replace('-', ' ').replace('_', ' '))?.value?.uppercase()
            return if (gemma != null) listOfNotNull("Gemma ${gemma.groupValues[1]}", size, quant).joinToString(" ") else n.take(22)
        }
}

/**
 * Model Hub ala PocketPal AI:
 * - Pemindai otomatis .gguf / .litertlm / .task di folder Download (izin Akses Semua File).
 * - Pemilih file universal (File Manager, ZArchiver, Google Drive): file lokal dibaca langsung (zero-copy),
 *   file dari cloud disalin di latar oleh WorkManager (buffer 64 KB) ke Download/GemmaModels.
 * - Pengunduh dari link Google Drive via WorkManager (file .tmp → rename setelah 100%).
 */
class ModelRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val beforeReplace: () -> Unit,
) {
    private val prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
    private val legacyInternal = File(File(context.filesDir, "models"), "gemma-model.litertlm")

    private val _copyState = MutableStateFlow<CopyState>(CopyState.Idle)
    val copyState: StateFlow<CopyState> = _copyState.asStateFlow()

    private val _models = MutableStateFlow<List<ModelFile>>(emptyList())
    val models: StateFlow<List<ModelFile>> = _models.asStateFlow()

    private val _active = MutableStateFlow<ModelFile?>(null)
    val active: StateFlow<ModelFile?> = _active.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    init {
        refresh()
        observeCopyWork()
    }

    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Folder tujuan salinan/unduhan: Download/GemmaModels (atau folder aplikasi bila tanpa izin). */
    fun modelsFolder(): File {
        val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GemmaModels")
        if (hasStorageAccess() && (pub.isDirectory || pub.mkdirs())) return pub
        return File(context.getExternalFilesDir(null), "GemmaModels").apply { mkdirs() }
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            _scanning.value = true
            try {
                val list = scan()
                _models.value = list
                val selected = settings.current.selectedModelPath
                _active.value = list.firstOrNull { it.path == selected }
                    ?: list.firstOrNull { it.name.contains("gemma4-latih", true) }
                    ?: list.firstOrNull { it.name.equals(PREFERRED_LITERT, true) }
                    ?: list.firstOrNull()
            } catch (_: Throwable) {
                _models.value = emptyList()
                _active.value = null
            } finally {
                _scanning.value = false
            }
        }
    }

    fun select(model: ModelFile) {
        settings.update { it.copy(selectedModelPath = model.path) }
        _active.value = model
    }

    private fun isModelFile(f: File) = f.isFile && f.length() > MIN_MODEL_BYTES &&
        (f.name.endsWith(".gguf", true) || f.name.endsWith(".litertlm", true) || f.name.endsWith(".task", true))

    private fun scan(): List<ModelFile> {
        val out = LinkedHashMap<String, ModelFile>()
        fun add(f: File, src: ModelSource) {
            val key = runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)
            if (!out.containsKey(key) && f.canRead()) out[key] = ModelFile(f.absolutePath, f.name, f.length(), src)
        }
        if (hasStorageAccess()) {
            listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File("/storage/emulated/0/Download"),
            ).filter { it.isDirectory }.forEach { root ->
                root.walkTopDown().maxDepth(3).onFail { _, _ -> }.filter { isModelFile(it) }.forEach {
                    add(it, if (it.parentFile?.name == "GemmaModels") ModelSource.APP_FOLDER else ModelSource.DOWNLOAD_DIRECT)
                }
            }
        }
        context.getExternalFilesDir(null)?.let { File(it, "GemmaModels") }?.listFiles()?.filter { isModelFile(it) }
            ?.forEach { add(it, ModelSource.APP_FOLDER) }
        // Path tambahan yang dipilih lewat File Manager / ZArchiver (zero-copy).
        prefs.getStringSet(KEY_EXTRA, emptySet())?.map { File(it) }?.filter { isModelFile(it) }
            ?.forEach { add(it, ModelSource.DOWNLOAD_DIRECT) }
        if (legacyInternal.isFile && legacyInternal.length() > MIN_MODEL_BYTES) add(legacyInternal, ModelSource.INTERNAL_COPY)
        return out.values.sortedWith(compareBy<ModelFile> { it.type.ordinal }.thenBy { it.name.lowercase() })
    }

    fun deleteLegacyCopy() {
        scope.launch(Dispatchers.IO) {
            if (_active.value?.source == ModelSource.INTERNAL_COPY) runCatching { beforeReplace() }
            legacyInternal.delete()
            refresh()
        }
    }

    // ---------------------- Pemilih file universal ----------------------

    /** Dipanggil setelah pengguna memilih file lewat OpenDocument (filter semua tipe file). */
    fun importFromUri(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val (name, size) = queryNameSize(uri)
                if (!(name.endsWith(".gguf", true) || name.endsWith(".litertlm", true) || name.endsWith(".task", true))) {
                    _copyState.value = CopyState.Error("\"$name\" bukan file model (.gguf / .litertlm / .task).")
                    return@launch
                }
                val local = resolveLocalPath(uri, name, size)
                if (local != null) {
                    // Zero-copy: pakai file aslinya.
                    val set = prefs.getStringSet(KEY_EXTRA, emptySet())!!.toMutableSet().apply { add(local.absolutePath) }
                    prefs.edit().putStringSet(KEY_EXTRA, set).apply()
                    settings.update { it.copy(selectedModelPath = local.absolutePath) }
                    _copyState.value = CopyState.Done("✅ $name dibaca langsung dari ${local.parent} (tanpa salin).")
                    refresh()
                    return@launch
                }
                // Sumber cloud (mis. Google Drive): salin di latar dengan WorkManager.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val target = File(modelsFolder(), name)
                val free = target.parentFile?.usableSpace ?: 0L
                if (size > 0 && free < size + 200L * 1024 * 1024) {
                    _copyState.value = CopyState.Error("Ruang penyimpanan tidak cukup: butuh ${com.gemmaagent.app.util.Fmt.gb(size)} GB.")
                    return@launch
                }
                val req = OneTimeWorkRequestBuilder<ModelCopyWorker>()
                    .setInputData(workDataOf(
                        ModelCopyWorker.KEY_URI to uri.toString(),
                        ModelCopyWorker.KEY_TARGET to target.absolutePath,
                        ModelCopyWorker.KEY_SIZE to size,
                    ))
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(COPY_WORK, ExistingWorkPolicy.KEEP, req)
                _copyState.value = CopyState.Copying(0, 0, size, "Menyalin dari cloud")
            } catch (t: Throwable) {
                _copyState.value = CopyState.Error("Gagal membuka file: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    /** Batalkan salin/unduh yang sedang berjalan. */
    fun cancelCopy() {
        WorkManager.getInstance(context).cancelUniqueWork(COPY_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(DOWNLOAD_WORK)
    }

    private fun observeCopyWork() {
        observeWork(COPY_WORK, "Menyalin dari cloud", ModelCopyWorker.KEY_PERCENT, ModelCopyWorker.KEY_COPIED,
            ModelCopyWorker.KEY_TOTAL, ModelCopyWorker.KEY_TARGET, ModelCopyWorker.KEY_ERROR)
        observeWork(DOWNLOAD_WORK, "Mengunduh dari Drive", DriveDownloadWorker.KEY_PERCENT, DriveDownloadWorker.KEY_DONE,
            DriveDownloadWorker.KEY_TOTAL, DriveDownloadWorker.KEY_TARGET, DriveDownloadWorker.KEY_ERROR)
    }

    private fun observeWork(name: String, label: String, kPct: String, kDone: String, kTotal: String, kTarget: String, kError: String) {
        scope.launch {
            var active = false
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(name).collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        active = true
                        val p = info.progress
                        _copyState.value = CopyState.Copying(p.getInt(kPct, 0), p.getLong(kDone, 0), p.getLong(kTotal, 0), label)
                    }
                    WorkInfo.State.SUCCEEDED -> if (active) {
                        active = false
                        val path = info.outputData.getString(kTarget)
                        if (path != null) settings.update { it.copy(selectedModelPath = path) }
                        _copyState.value = CopyState.Done("✅ Selesai: ${path?.let { File(it).name } ?: "model"} siap dipakai.")
                        refresh()
                    }
                    WorkInfo.State.FAILED -> if (active) {
                        active = false
                        _copyState.value = CopyState.Error(info.outputData.getString(kError) ?: "$label gagal.")
                    }
                    WorkInfo.State.CANCELLED -> if (active) {
                        active = false
                        _copyState.value = CopyState.Error("$label dibatalkan.")
                    }
                }
            }
        }
    }

    private fun queryNameSize(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "model"
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        return name to size
    }

    /**
     * Mencoba menemukan path file asli dari URI (File Manager bawaan, ZArchiver, folder Download).
     * Mengembalikan null bila file berasal dari cloud (mis. Google Drive).
     */
    private fun resolveLocalPath(uri: Uri, name: String, size: Long): File? {
        fun ok(f: File?) = f != null && f.isFile && f.canRead() && (size <= 0 || f.length() == size)
        if (uri.scheme == "file") uri.path?.let { File(it) }?.takeIf { ok(it) }?.let { return it }
        val authority = uri.authority.orEmpty()
        if (authority.contains("google") && authority.contains("docs")) return null // Google Drive

        runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                when (authority) {
                    "com.android.externalstorage.documents" -> {
                        val (vol, rel) = docId.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                        val base = if (vol.equals("primary", true)) Environment.getExternalStorageDirectory().absolutePath else "/storage/$vol"
                        File(base, rel).takeIf { ok(it) }?.let { return it }
                    }
                    "com.android.providers.downloads.documents" -> {
                        if (docId.startsWith("raw:")) File(docId.removePrefix("raw:")).takeIf { ok(it) }?.let { return it }
                    }
                }
            }
        }
        // ZArchiver / file manager lain sering menyertakan path asli di URI.
        val decoded = Uri.decode(uri.toString())
        val idx = decoded.indexOf("/storage/")
        if (idx >= 0) File(decoded.substring(idx)).takeIf { ok(it) }?.let { return it }
        uri.path?.let { p -> p.indexOf("/storage/").takeIf { it >= 0 }?.let { File(p.substring(it)) } }?.takeIf { ok(it) }?.let { return it }

        // Terakhir: cari file bernama & berukuran sama di folder Download (butuh izin Akses Semua File).
        if (hasStorageAccess()) {
            val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dl.walkTopDown().maxDepth(3).onFail { _, _ -> }.firstOrNull { it.isFile && it.name == name && (size <= 0 || it.length() == size) }
                ?.let { return it }
        }
        return null
    }

    // ---------------------- Unduh dari Google Drive (WorkManager) ----------------------

    /**
     * Mengubah link berbagi Drive menjadi link unduhan langsung lalu mengunduh di latar
     * (WorkManager, buffer 64 KB). File ditulis ke ".tmp" dan baru diganti nama setelah 100%.
     */
    fun downloadFromDrive(linkOrId: String, fileName: String) {
        val id = extractDriveId(linkOrId)
        if (id == null) {
            _copyState.value = CopyState.Error("Link Drive tidak dikenali. Gunakan link file (…/file/d/ID/view), bukan link folder.")
            return
        }
        val name = fileName.trim().ifBlank { "model-$id.gguf" }
        if (!(name.endsWith(".gguf", true) || name.endsWith(".litertlm", true) || name.endsWith(".task", true))) {
            _copyState.value = CopyState.Error("Nama file harus berakhiran .gguf / .litertlm / .task.")
            return
        }
        val target = File(modelsFolder(), name)
        if (target.exists() && target.length() > MIN_MODEL_BYTES) {
            _copyState.value = CopyState.Done("✅ $name sudah ada di ${target.parent}. Tidak perlu diunduh lagi.")
            refresh()
            return
        }
        val req = OneTimeWorkRequestBuilder<DriveDownloadWorker>()
            .setInputData(workDataOf(
                DriveDownloadWorker.KEY_URL to "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t",
                DriveDownloadWorker.KEY_TARGET to target.absolutePath,
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DOWNLOAD_WORK, ExistingWorkPolicy.KEEP, req)
        _copyState.value = CopyState.Copying(0, 0, 0, "Mengunduh dari Drive")
    }

    fun cancelDownload() {
        WorkManager.getInstance(context).cancelUniqueWork(DOWNLOAD_WORK)
    }

    companion object {
        const val PREFERRED_LITERT = "gemma-4-E2B-it.litertlm"
        const val USER_DRIVE_FOLDER = "https://drive.google.com/drive/folders/1rv8D1k-5xcaXvYZ8QbrMyV7eGLqs6NxL"
        /** File GGUF hasil latih ulang pengguna di folder Drive di atas (model_hp/gguf). */
        const val USER_DRIVE_GGUF_ID = "1vKHZZIx9yrU4QQ4WdvLXV4vgyYkKLNEY"
        const val USER_DRIVE_GGUF_NAME = "gemma4-latih-Q4_K_M.gguf"
        private const val COPY_WORK = "model_copy"
        private const val DOWNLOAD_WORK = "model_download"
        private const val KEY_EXTRA = "extra_paths"
        private const val MIN_MODEL_BYTES = 50L * 1024 * 1024

        fun extractDriveId(s: String): String? {
            val t = s.trim()
            Regex("/file/d/([A-Za-z0-9_-]{20,})").find(t)?.let { return it.groupValues[1] }
            Regex("[?&]id=([A-Za-z0-9_-]{20,})").find(t)?.let { return it.groupValues[1] }
            if (t.contains("/folders/")) return null
            return t.takeIf { Regex("^[A-Za-z0-9_-]{20,}$").matches(it) }
        }
    }
}
