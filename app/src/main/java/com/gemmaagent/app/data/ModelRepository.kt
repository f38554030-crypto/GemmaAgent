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

/** Status proses penyalinan model (cara cadangan lewat File Picker). */
sealed interface CopyState {
    data object Idle : CopyState
    data class Copying(val percent: Int, val copiedBytes: Long, val totalBytes: Long) : CopyState
    data class Done(val message: String) : CopyState
    data class Error(val message: String) : CopyState
}

enum class ModelSource {
    /** Dibaca langsung dari /storage/emulated/0/Download (tanpa salin). */
    DOWNLOAD_DIRECT,

    /** Salinan di memori internal aplikasi (filesDir). */
    INTERNAL_COPY,
    NONE,
}

data class ModelInfo(
    val source: ModelSource,
    val path: String?,
    val name: String,
    val sizeBytes: Long,
) {
    val ready: Boolean get() = source != ModelSource.NONE && path != null
}

/**
 * Mengelola lokasi file model .litertlm.
 *
 * Cara utama: dengan izin "Akses Semua File" (MANAGE_EXTERNAL_STORAGE), aplikasi
 * mendeteksi otomatis gemma-4-E2B-it.litertlm di folder Download dan membacanya
 * LANGSUNG dari sana (tanpa menyalin 2,59 GB).
 *
 * Cara cadangan: pilih file lewat File Picker → disalin sekali ke filesDir.
 */
class ModelRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val beforeReplace: () -> Unit,
) {
    private val prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
    private val modelDir = File(context.filesDir, "models")

    /** Lokasi salinan internal (cara cadangan). */
    val internalFile: File = File(modelDir, "gemma-model.litertlm")

    private val _copyState = MutableStateFlow<CopyState>(CopyState.Idle)
    val copyState: StateFlow<CopyState> = _copyState.asStateFlow()

    private val _info = MutableStateFlow(ModelInfo(ModelSource.NONE, null, "Belum ada model", 0))
    val info: StateFlow<ModelInfo> = _info.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var copyJob: Job? = null

    init {
        refresh()
    }

    /** Path model yang dipakai mesin AI saat ini (null jika belum ada). */
    fun currentModelPath(): String? = _info.value.path

    /** Apakah izin baca penyimpanan (Akses Semua File) sudah diberikan. */
    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Pindai ulang folder Download dan salinan internal. Aman dipanggil kapan saja. */
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            _scanning.value = true
            try {
                _info.value = detect()
            } catch (t: Throwable) {
                _info.value = ModelInfo(ModelSource.NONE, null, "Gagal memindai: ${t.message ?: t.javaClass.simpleName}", 0)
            } finally {
                _scanning.value = false
            }
        }
    }

    private fun detect(): ModelInfo {
        // 1) Prioritas: baca langsung dari folder Download.
        if (hasStorageAccess()) {
            findInDownload()?.let { f ->
                return ModelInfo(ModelSource.DOWNLOAD_DIRECT, f.absolutePath, f.name, f.length())
            }
        }
        // 2) Cadangan: salinan internal yang lengkap.
        val expected = prefs.getLong(KEY_SIZE, -1L)
        if (internalFile.isFile && internalFile.length() > 0 && internalFile.length() == expected) {
            return ModelInfo(
                ModelSource.INTERNAL_COPY,
                internalFile.absolutePath,
                prefs.getString(KEY_NAME, null) ?: internalFile.name,
                internalFile.length(),
            )
        }
        return ModelInfo(ModelSource.NONE, null, "Belum ada model", 0)
    }

    private fun findInDownload(): File? {
        val roots = buildList {
            add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            add(File("/storage/emulated/0/Download"))
        }.distinctBy { it.absolutePath }

        for (root in roots) {
            val exact = File(root, PREFERRED_NAME)
            if (exact.isFile && exact.canRead() && exact.length() > MIN_MODEL_BYTES) return exact
        }
        val candidates = roots.flatMap { root ->
            if (!root.isDirectory) emptyList()
            else root.walkTopDown()
                .maxDepth(2)
                .onFail { _, _ -> }
                .filter { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) && it.length() > MIN_MODEL_BYTES && it.canRead() }
                .toList()
        }
        return candidates.sortedWith(
            compareByDescending<File> { it.name.contains("gemma-4", true) && it.name.contains("e2b", true) }
                .thenByDescending { it.length() }
        ).firstOrNull()
    }

    /** Menghapus salinan internal (hemat ±2,6 GB) bila model sudah dibaca langsung dari Download. */
    fun deleteInternalCopy() {
        scope.launch(Dispatchers.IO) {
            try {
                if (_info.value.source == ModelSource.INTERNAL_COPY) beforeReplace()
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

    // ------------------------------------------------------------------
    // CARA CADANGAN: File Picker → salin ke filesDir
    // ------------------------------------------------------------------

    fun importModel(uri: Uri) {
        if (copyJob?.isActive == true) return
        copyJob = scope.launch(Dispatchers.IO) {
            try {
                copyInternal(uri)
            } catch (oom: OutOfMemoryError) {
                _copyState.value = CopyState.Error("Memori RAM tidak cukup saat menyalin. Tutup aplikasi lain lalu coba lagi.")
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
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
        }

        if (!name.lowercase().endsWith(".litertlm")) {
            _copyState.value = CopyState.Error("File \"$name\" bukan model .litertlm. Pilih file Gemma 4 E2B IT berekstensi .litertlm.")
            return
        }

        // Cukup satu kali: kalau file yang sama sudah tersalin, lewati.
        if (internalFile.isFile && prefs.getString(KEY_NAME, null) == name &&
            size > 0 && prefs.getLong(KEY_SIZE, -1L) == size && internalFile.length() == size
        ) {
            _copyState.value = CopyState.Done("Model \"$name\" sudah tersalin sebelumnya. Tidak perlu menyalin ulang.")
            return
        }

        modelDir.mkdirs()
        if (size > 0) {
            val needed = size + 200L * 1024 * 1024
            val available = modelDir.usableSpace + (if (internalFile.exists()) internalFile.length() else 0L)
            if (available < needed) {
                _copyState.value = CopyState.Error(
                    "Memori internal tidak cukup. Butuh sekitar ${Fmt.gb(needed)} GB ruang kosong, tersedia ${Fmt.gb(available)} GB."
                )
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
        var lastEmitBytes = 0L
        input.use { ins ->
            temp.outputStream().use { out ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = ins.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    copied += read
                    if (size > 0) {
                        val percent = ((copied * 100) / size).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _copyState.value = CopyState.Copying(percent, copied, size)
                        }
                    } else if (copied - lastEmitBytes >= 50L * 1024 * 1024) {
                        lastEmitBytes = copied
                        _copyState.value = CopyState.Copying(0, copied, size)
                    }
                }
                out.flush()
                runCatching { out.fd.sync() }
            }
        }

        if (size > 0 && copied != size) {
            temp.delete()
            _copyState.value = CopyState.Error("Penyalinan tidak lengkap (${Fmt.gb(copied)} dari ${Fmt.gb(size)} GB). Coba lagi.")
            return
        }

        // Lepaskan model lama dari RAM sebelum file-nya diganti.
        runCatching { beforeReplace() }
        internalFile.delete()
        if (!temp.renameTo(internalFile)) {
            temp.delete()
            _copyState.value = CopyState.Error("Gagal menyimpan model ke memori internal.")
            return
        }
        prefs.edit().putString(KEY_NAME, name).putLong(KEY_SIZE, internalFile.length()).apply()
        _copyState.value = CopyState.Done("Model \"$name\" berhasil disalin (${Fmt.gb(copied)} GB).")
    }

    companion object {
        const val PREFERRED_NAME = "gemma-4-E2B-it.litertlm"
        private const val MIN_MODEL_BYTES = 100L * 1024 * 1024
        private const val KEY_NAME = "model_name"
        private const val KEY_SIZE = "model_size"
    }
}
