package com.gemmaagent.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Status proses penyalinan model dari File Picker ke memori internal aplikasi. */
sealed interface CopyState {
    data object Idle : CopyState
    data class Copying(val percent: Int, val copiedBytes: Long, val totalBytes: Long) : CopyState
    data class Done(val message: String) : CopyState
    data class Error(val message: String) : CopyState
}

/**
 * Mengelola file model .litertlm.
 * File dari folder Download disalin SATU KALI ke context.filesDir/models,
 * supaya mesin C++ LiteRT-LM bisa membacanya langsung lewat path biasa.
 */
class ModelRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val beforeReplace: () -> Unit,
) {
    private val prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
    private val modelDir = File(context.filesDir, "models")
    val modelFile: File = File(modelDir, "gemma-model.litertlm")

    private val _copyState = MutableStateFlow<CopyState>(CopyState.Idle)
    val copyState: StateFlow<CopyState> = _copyState.asStateFlow()

    private val _modelReady = MutableStateFlow(checkModelPresent())
    val modelReady: StateFlow<Boolean> = _modelReady.asStateFlow()

    val modelDisplayName: String
        get() = prefs.getString(KEY_NAME, null) ?: "Belum ada model"

    private fun checkModelPresent(): Boolean {
        val expected = prefs.getLong(KEY_SIZE, -1L)
        return modelFile.exists() && modelFile.length() > 0 && modelFile.length() == expected
    }

    fun refresh() {
        _modelReady.value = checkModelPresent()
    }

    fun importModel(uri: Uri) {
        if (_copyState.value is CopyState.Copying) return
        scope.launch(Dispatchers.IO) {
            try {
                copyInternal(uri)
            } catch (t: Throwable) {
                _copyState.value = CopyState.Error("Gagal menyalin: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                File(modelDir, "${modelFile.name}.part").delete()
                refresh()
            }
        }
    }

    private fun copyInternal(uri: Uri) {
        val resolver = context.contentResolver
        var name = "model.litertlm"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }

        if (!name.lowercase().endsWith(".litertlm")) {
            _copyState.value = CopyState.Error("File \"$name\" bukan model .litertlm. Pilih file Gemma 4 E2B IT berekstensi .litertlm.")
            return
        }

        // Penyalinan cukup satu kali: kalau file yang sama sudah ada, lewati.
        if (checkModelPresent() && prefs.getString(KEY_NAME, null) == name &&
            size > 0 && prefs.getLong(KEY_SIZE, -1L) == size
        ) {
            _copyState.value = CopyState.Done("Model \"$name\" sudah tersalin sebelumnya. Tidak perlu menyalin ulang.")
            return
        }

        modelDir.mkdirs()
        val needed = if (size > 0) size + 200L * 1024 * 1024 else 0L
        if (needed > 0 && modelDir.usableSpace + (if (modelFile.exists()) modelFile.length() else 0L) < needed) {
            val gb = needed / (1024.0 * 1024 * 1024)
            _copyState.value = CopyState.Error("Memori internal tidak cukup. Butuh sekitar %.1f GB ruang kosong.".format(gb))
            return
        }

        val temp = File(modelDir, "${modelFile.name}.part")
        temp.delete()
        _copyState.value = CopyState.Copying(0, 0, size)

        val input = resolver.openInputStream(uri) ?: run {
            _copyState.value = CopyState.Error("File tidak bisa dibuka.")
            return
        }
        var copied = 0L
        var lastPercent = -1
        input.use { ins ->
            temp.outputStream().use { out ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = ins.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    copied += read
                    val percent = if (size > 0) ((copied * 100) / size).toInt().coerceIn(0, 100) else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _copyState.value = CopyState.Copying(percent, copied, size)
                    } else if (size <= 0 && copied % (50L * 1024 * 1024) < read) {
                        _copyState.value = CopyState.Copying(0, copied, size)
                    }
                }
                out.fd.sync()
            }
        }

        if (size > 0 && copied != size) {
            temp.delete()
            _copyState.value = CopyState.Error("Penyalinan tidak lengkap ($copied dari $size byte). Coba lagi.")
            return
        }

        // Lepaskan model lama dari RAM sebelum file-nya diganti.
        beforeReplace()
        modelFile.delete()
        if (!temp.renameTo(modelFile)) {
            temp.delete()
            _copyState.value = CopyState.Error("Gagal menyimpan model ke memori internal.")
            return
        }
        prefs.edit().putString(KEY_NAME, name).putLong(KEY_SIZE, modelFile.length()).apply()
        _copyState.value = CopyState.Done("Model \"$name\" berhasil disalin (%.2f GB).".format(copied / (1024.0 * 1024 * 1024)))
    }

    companion object {
        private const val KEY_NAME = "model_name"
        private const val KEY_SIZE = "model_size"
    }
}
