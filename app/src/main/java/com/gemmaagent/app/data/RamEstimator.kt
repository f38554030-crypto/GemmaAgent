package com.gemmaagent.app.data

import com.gemmaagent.app.llm.EngineType
import com.gemmaagent.app.llm.ModelSettings
import java.io.File

/** Perkiraan pemakaian RAM sebuah model (bobot + KV cache + buffer kerja). */
data class RamEstimate(val totalBytes: Long, val weightsBytes: Long, val kvBytes: Long, val overheadBytes: Long, val fromHeader: Boolean)

/**
 * Estimasi kasar & jujur:
 * - GGUF: bobot ≈ ukuran file (dibaca lewat mmap), KV cache dihitung dari header GGUF
 *   (n_ctx × n_layer × head_kv × (dim_k + dim_v) × 2 byte f16), + buffer kerja ±300 MB.
 * - LiteRT-LM: ukuran file + buffer ±450 MB.
 * Nilai sebenarnya bisa lebih kecil (mmap hanya memuat bagian yang dipakai).
 */
object RamEstimator {
    private val cache = HashMap<String, RamEstimate>()

    @Synchronized
    fun estimate(model: ModelFile, s: ModelSettings): RamEstimate {
        val key = "${model.path}|${model.sizeBytes}|${s.nCtx}"
        cache[key]?.let { return it }
        val r = compute(model, s)
        cache[key] = r
        return r
    }

    private fun compute(model: ModelFile, s: ModelSettings): RamEstimate {
        val weights = model.sizeBytes
        if (model.type == EngineType.LITERT) {
            val overhead = 450L * MB
            return RamEstimate(weights + overhead, weights, 0, overhead, false)
        }
        val md = runCatching { GgufReader.read(File(model.path)) }.getOrNull()
        var fromHeader = false
        val kv = if (md?.blockCount != null && md.embeddingLength != null) {
            val nLayer = md.blockCount
            val nHead = md.headCount?.takeIf { it > 0 } ?: 8
            val nHeadKv = md.headCountKv?.takeIf { it > 0 } ?: nHead
            val headDim = md.embeddingLength / nHead
            val k = md.keyLength?.takeIf { it > 0 } ?: headDim
            val v = md.valueLength?.takeIf { it > 0 } ?: headDim
            fromHeader = true
            s.nCtx.toLong() * nLayer * nHeadKv * (k + v) * 2L
        } else {
            // Tanpa info header: ±0,1 MB per token konteks.
            s.nCtx.toLong() * 100L * 1024
        }
        val overhead = 300L * MB
        return RamEstimate(weights + kv + overhead, weights, kv, overhead, fromHeader)
    }

    private const val MB = 1024L * 1024
}
