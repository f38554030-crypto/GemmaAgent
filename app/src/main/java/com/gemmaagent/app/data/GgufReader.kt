package com.gemmaagent.app.data

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Hasil Inspektor Metadata Model (fitur 7). */
data class ModelMetadata(
    val architecture: String?,
    val name: String?,
    val quantization: String?,
    val contextLength: Long?,
    val blockCount: Long?,
    val embeddingLength: Long?,
    val chatTemplate: String?,
    val ggufVersion: Int?,
    val tensorCount: Long?,
    val extra: Map<String, String> = emptyMap(),
    val headCount: Long? = null,
    val headCountKv: Long? = null,
    val keyLength: Long? = null,
    val valueLength: Long? = null,
    val slidingWindow: Long? = null,
)

/**
 * Pembaca header GGUF murni Kotlin: hanya membaca bagian metadata di awal file
 * (tidak memuat bobot model), jadi cepat & hemat RAM.
 */
object GgufReader {
    private const val MAGIC = 0x46554747 // "GGUF" little-endian

    private val fileTypes = mapOf(
        0 to "F32", 1 to "F16", 2 to "Q4_0", 3 to "Q4_1", 7 to "Q8_0", 8 to "Q5_0", 9 to "Q5_1",
        10 to "Q2_K", 11 to "Q3_K_S", 12 to "Q3_K_M", 13 to "Q3_K_L", 14 to "Q4_K_S", 15 to "Q4_K_M",
        16 to "Q5_K_S", 17 to "Q5_K_M", 18 to "Q6_K", 19 to "IQ2_XXS", 20 to "IQ2_XS", 21 to "Q2_K_S",
        22 to "IQ3_XS", 23 to "IQ3_XXS", 24 to "IQ1_S", 25 to "IQ4_NL", 26 to "IQ3_S", 27 to "IQ3_M",
        28 to "IQ2_S", 29 to "IQ2_M", 30 to "IQ4_XS", 31 to "IQ1_M", 32 to "BF16", 36 to "TQ1_0", 37 to "TQ2_0",
    )

    private class LeReader(file: File) : AutoCloseable {
        private val input = DataInputStream(BufferedInputStream(FileInputStream(file), 1 shl 16))
        private val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

        private fun read(n: Int): ByteBuffer {
            buf.clear()
            input.readFully(buf.array(), 0, n)
            buf.limit(n)
            return buf
        }

        fun u8() = input.readUnsignedByte()
        fun i8() = input.readByte().toInt()
        fun u16() = read(2).short.toInt() and 0xFFFF
        fun i16() = read(2).short.toInt()
        fun u32() = read(4).int.toLong() and 0xFFFFFFFFL
        fun i32() = read(4).int
        fun u64() = read(8).long
        fun f32() = read(4).float
        fun f64() = read(8).double
        fun str(maxKeep: Int = 4096): String {
            val len = u64()
            if (len < 0 || len > 50_000_000) throw IllegalStateException("panjang string tidak wajar")
            val keep = minOf(len, maxKeep.toLong()).toInt()
            val bytes = ByteArray(keep)
            input.readFully(bytes)
            var skip = len - keep
            while (skip > 0) {
                val n = input.skip(skip)
                if (n > 0) skip -= n else if (input.read() < 0) throw IllegalStateException("file terpotong") else skip--
            }
            return String(bytes, Charsets.UTF_8)
        }

        override fun close() = input.close()
    }

    fun read(file: File): ModelMetadata {
        LeReader(file).use { r ->
            if (r.i32() != MAGIC) throw IllegalStateException("Bukan file GGUF")
            val version = r.i32()
            val tensorCount = r.u64()
            val kvCount = r.u64()
            val kv = HashMap<String, Any?>()
            var i = 0L
            while (i < kvCount && i < 10_000) {
                val key = r.str(256)
                val type = r.i32()
                val keepLong = key == "tokenizer.chat_template"
                kv[key] = readValue(r, type, if (keepLong) 20_000 else 512)
                i++
            }
            val arch = kv["general.architecture"] as? String
            fun num(k: String) = (kv[k] as? Number)?.toLong()
            val ft = num("general.file_type")?.toInt()
            return ModelMetadata(
                architecture = arch,
                name = kv["general.name"] as? String,
                quantization = ft?.let { fileTypes[it] ?: "tipe $it" },
                contextLength = arch?.let { num("$it.context_length") },
                blockCount = arch?.let { num("$it.block_count") },
                embeddingLength = arch?.let { num("$it.embedding_length") },
                chatTemplate = kv["tokenizer.chat_template"] as? String,
                ggufVersion = version,
                tensorCount = tensorCount,
                headCount = arch?.let { num("$it.attention.head_count") },
                headCountKv = arch?.let { num("$it.attention.head_count_kv") },
                keyLength = arch?.let { num("$it.attention.key_length") },
                valueLength = arch?.let { num("$it.attention.value_length") },
                slidingWindow = arch?.let { num("$it.attention.sliding_window") },
                extra = listOfNotNull(
                    (kv["general.basename"] as? String)?.let { "basename" to it },
                    (kv["general.size_label"] as? String)?.let { "size_label" to it },
                    (kv["general.finetune"] as? String)?.let { "finetune" to it },
                    (kv["tokenizer.ggml.model"] as? String)?.let { "tokenizer" to it },
                ).toMap(),
            )
        }
    }

    /** Membaca satu nilai; array dilewati (kecuali jumlahnya). */
    private fun readValue(r: LeReader, type: Int, maxKeep: Int): Any? = when (type) {
        0 -> r.u8(); 1 -> r.i8(); 2 -> r.u16(); 3 -> r.i16(); 4 -> r.u32(); 5 -> r.i32()
        6 -> r.f32(); 7 -> r.u8() != 0; 8 -> r.str(maxKeep)
        9 -> {
            val itemType = r.i32()
            val n = r.u64()
            var k = 0L
            while (k < n) { readValue(r, itemType, 0); k++ }
            "[array $n]"
        }
        10 -> r.u64(); 11 -> r.u64(); 12 -> r.f64()
        else -> throw IllegalStateException("tipe GGUF tidak dikenal: $type")
    }
}
