package com.gemmaagent.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.gemmaagent.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Status penyalinan model (cara cadangan lewat File Picker). */
sealed interface CopyState {
    data object Idle : CopyState
    data class Copying(val percent: Int, val copiedBytes: Long, val totalBytes: Long) : CopyState
    data class Done(val message: String) : CopyState
    data class Error(val message: String) : CopyState
}

enum class ModelSource { DOWNLOAD_DIRECT, INTERNAL_COPY }

data class ModelFile(val path: String, val name: String, val sizeBytes: Long, val source: ModelSource) {
    /** Label pendek untuk Top App Bar, mis. "Gemma 4 E2B". */
    val shortLabel: String
        get() {
            val n = name.removeSuffix(".litertlm")
            val gemma = Regex("gemma[-_ ]?(\\d+n?)[-_ ]?([a-z0-9]+)?", RegexOption.IGNORE_CASE).find(n)
            return if (gemma != null) {
                val ver = gemma.groupValues[1]
                val size = gemma.groupValues[2].uppercase().takeIf { it.isNotEmpty() && it != "IT" }
                listOfNotNull("Gemma $ver", size).joinToString(" ")
            } else n.take(18)
        }
}

/**
 * Manajer Multi-Model (.litertlm):
 * - Pemuatan langsung (zero-copy) dari /storage/emulated/0/Download dengan izin "Akses Semua File".
 * - Pemindai otomatis semua file .litertlm di folder Download (sampai 2 tingkat subfolder).
 * - Cara cadangan: pilih file → disalin sekali ke memori internal.
 */
class ModelRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val beforeReplace: () -> Unit,
) {
    private val prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
    private val modelDir = File(context.filesDir, "models")
    val internalFile: File = File(modelDir, "gemma-model.litertlm")

    private val _copyState = MutableStateFlow<CopyState>(CopyState.Idle)
    val copyState: StateFlow<CopyState> = _copyState.asStateFlow()

    private val _models = MutableStateFlow<List<ModelFile>>(emptyList())
    val models: StateFlow<List<ModelFile>> = _models.asStateFlow()

    private val _active = MutableStateFlow<ModelFile?>(null)
    val active: StateFlow<ModelFile?> = _active.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var copyJob: Job? = null

    init {
        refresh()
    }

    fun currentModelPath(): String? = _active.value?.path

    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            _scanning.value = true
            try {
                val list = scan()
                _models.value = list
                val selected = settings.current.selectedModelPath
                _active.value = list.firstOrNull { it.path == selected }
                    ?: list.firstOrNull { it.name.equals(PREFERRED_NAME, true) }
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

    private fun scan(): List<ModelFile> {
        val result = mutableListOf<ModelFile>()
        if (hasStorageAccess()) {
            val roots = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File("/storage/emulated/0/Download"),
            ).distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            val seen = HashSet<String>()
            for (root in roots) {
                if (!root.isDirectory) continue
                root.walkTopDown().maxDepth(2).onFail { _, _ -> }
                    .filter { it.isFile && it.name.endsWith(".litertlm", true) && it.length() > MIN_MODEL_BYTES && it.canRead() }
                    .forEach { f ->
                        val key = runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)
                        if (seen.add(key)) result += ModelFile(f.absolutePath, f.name, f.length(), ModelSource.DOWNLOAD_DIRECT)
                    }
            }
        }
        val expected = prefs.getLong(KEY_SIZE, -1L)
        if (internalFile.isFile && internalFile.length() > 0 && internalFile.length() == expected) {
            result += ModelFile(
                internalFile.absolutePath,
                prefs.getString(KEY_NAME, null) ?: internalFile.name,
                internalFile.length(),
                ModelSource.INTERNAL_COPY,
            )
        }
        return result.sortedWith(
            compareByDescending<ModelFile> { it.name.equals(PREFERRED_NAME, true) }
                .thenBy { it.source.ordinal }
                .thenBy { it.name.lowercase() }
        )
    }

    fun deleteInternalCopy() {
        scope.launch(Dispatchers.IO) {
            try {
                if (_active.value?.source == ModelSource.INTERNAL_COPY) runCatching { beforeReplace() }
                internalFile.delete()
                prefs.edit().remove(KEY_NAME).remove(KEY_SIZE).apply()
                _copyState.value = CopyState.Done("Salinan internal dihapus.")
            } catch (t: Throwable) {
                _copyState.value = CopyState.Error("Gagal menghapus salinan: ${t.message}")
            } finally {
                refresh()
            }
        }
    }

    // ---------------- Cara cadangan: salin dari File Picker ----------------

    fun importModel(uri: Uri) {
        if (copyJob?.isActive == true) return
        copyJob = scope.launch(Dispatchers.IO) {
            try {
                copyInternal(uri)
            } catch (_: OutOfMemoryError) {
                _copyState.value = CopyState.Error("RAM tidak cukup saat menyalin. Tutup aplikasi lain lalu coba lagi.")
            } catch (t: Throwable) {
                _copyState.value = CopyState.Error("Gagal menyalin: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                runCatching { File(modelDir, "${internalFile.name}.part").delete() }
                refresh()
            }
        }
    }

    private fun copyInternal(uri: Uri) {
        val resolver = context.contentResolver
        var name = "model.litertlm"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        if (!name.lowercase().endsWith(".litertlm")) {
            _copyState.value = CopyState.Error("File \"$name\" bukan model .litertlm.")
            return
        }
        if (internalFile.isFile && prefs.getString(KEY_NAME, null) == name && size > 0 && internalFile.length() == size) {
            _copyState.value = CopyState.Done("Model \"$name\" sudah tersalin sebelumnya.")
            return
        }
        modelDir.mkdirs()
        if (size > 0) {
            val needed = size + 200L * 1024 * 1024
            val available = modelDir.usableSpace + (if (internalFile.exists()) internalFile.length() else 0L)
            if (available < needed) {
                _copyState.value = CopyState.Error("Memori internal tidak cukup. Butuh ${Fmt.gb(needed)} GB, tersedia ${Fmt.gb(available)} GB.")
                return
            }
        }
        val temp = File(modelDir, "${internalFile.name}.part")
        temp.delete()
        _copyState.value = CopyState.Copying(0, 0, size)
        val input = resolver.openInputStream(uri) ?: run {
            _copyState.value = CopyState.Error("File tidak bisa dibuka.")
            return
        }
        var copied = 0L
        var lastPercent = -1
        var lastEmit = 0L
        input.use { ins ->
            temp.outputStream().use { out ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = ins.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    copied += read
                    if (size > 0) {
                        val p = ((copied * 100) / size).toInt().coerceIn(0, 100)
                        if (p != lastPercent) {
                            lastPercent = p
                            _copyState.value = CopyState.Copying(p, copied, size)
                        }
                    } else if (copied - lastEmit >= 50L * 1024 * 1024) {
                        lastEmit = copied
                        _copyState.value = CopyState.Copying(0, copied, size)
                    }
                }
                out.flush()
                runCatching { out.fd.sync() }
            }
        }
        if (size > 0 && copied != size) {
            temp.delete()
            _copyState.value = CopyState.Error("Penyalinan tidak lengkap. Coba lagi.")
            return
        }
        runCatching { beforeReplace() }
        internalFile.delete()
        if (!temp.renameTo(internalFile)) {
            temp.delete()
            _copyState.value = CopyState.Error("Gagal menyimpan model.")
            return
        }
        prefs.edit().putString(KEY_NAME, name).putLong(KEY_SIZE, internalFile.length()).apply()
        settings.update { it.copy(selectedModelPath = internalFile.absolutePath) }
        _copyState.value = CopyState.Done("Model \"$name\" berhasil disalin (${Fmt.gb(copied)} GB).")
    }

    companion object {
        const val PREFERRED_NAME = "gemma-4-E2B-it.litertlm"
        private const val MIN_MODEL_BYTES = 100L * 1024 * 1024
        private const val KEY_NAME = "model_name"
        private const val KEY_SIZE = "model_size"
    }
}
