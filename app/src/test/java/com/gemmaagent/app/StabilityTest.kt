package com.gemmaagent.app

import com.gemmaagent.app.chat.StreamState
import com.gemmaagent.app.data.db.Kinds
import com.gemmaagent.app.data.db.MessageEntity
import com.gemmaagent.app.data.db.Roles
import com.gemmaagent.app.docs.DocumentReader
import com.gemmaagent.app.llm.EngineType
import com.gemmaagent.app.llm.LoopGuard
import com.gemmaagent.app.llm.ModelValidator
import com.gemmaagent.app.llm.OutputCleaner
import com.gemmaagent.app.ui.AiBlockRow
import com.gemmaagent.app.ui.AiFooterRow
import com.gemmaagent.app.ui.AiHeaderRow
import com.gemmaagent.app.ui.AiLiveRow
import com.gemmaagent.app.ui.ChatRowsBuilder
import com.gemmaagent.app.ui.UserRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StabilityTest {

    // ---------------- Pembersih output ----------------

    @Test fun cleanerStopsAtEndOfTurnSplitAcrossTokens() {
        val c = OutputCleaner()
        c.feed("Halo")
        c.feed(" dunia")
        assertEquals("Halo dunia", c.feed("<end_"))          // potongan token ditahan, tidak tampil
        assertFalse(c.stopped)
        c.feed("of_turn>")
        assertTrue(c.stopped)
        assertEquals("Halo dunia", c.visible)
        c.feed("teks setelah stop diabaikan")
        assertEquals("Halo dunia", c.finish())
    }

    @Test fun cleanerStopsOnPipeTokensAndEos() {
        val a = OutputCleaner(); a.feed("Jawaban<|eot_id|>user lagi"); assertTrue(a.stopped); assertEquals("Jawaban", a.visible)
        val b = OutputCleaner(); b.feed("Selesai.</s>"); assertTrue(b.stopped); assertEquals("Selesai.", b.visible)
        val c = OutputCleaner(); c.feed("Oke<|im_end|>"); assertEquals("Oke", c.visible)
        val d = OutputCleaner(); d.feed("X<|turn_end|>"); assertTrue(d.stopped)
    }

    @Test fun cleanerKeepsNormalHtmlAndMath() {
        val c = OutputCleaner()
        c.feed("Pakai <b>tebal</b> dan 3 < 5")
        assertFalse(c.stopped)
        assertEquals("Pakai <b>tebal</b> dan 3 < 5", c.visible)
    }

    @Test fun cleanerStripsPadTokens() {
        val c = OutputCleaner()
        c.feed("A<pad>B<unk>C")
        assertEquals("ABC", c.visible)
    }

    @Test fun cleanerCustomStopWord() {
        val c = OutputCleaner(listOf("### Instruction:"))
        c.feed("Jawab.\n### Instr")
        assertFalse(c.stopped)
        assertEquals("Jawab.\n", c.visible)               // awalan stop word ditahan
        c.feed("uction: lagi")
        assertTrue(c.stopped)
        assertEquals("Jawab.\n", c.visible)
    }

    // ---------------- Anti-looping ----------------

    @Test fun loopGuardDetectsRepeatedLines() {
        val g = LoopGuard()
        val text = "Intro\n" + "Saya akan membantu Anda.\n".repeat(5)
        val cut = g.check(text)
        assertNotNull(cut)
        assertEquals("Intro\nSaya akan membantu Anda.", text.take(cut!!))
    }

    @Test fun loopGuardIgnoresNormalTextAndDividers() {
        assertNull(LoopGuard().check("Ini paragraf normal yang panjang tentang banyak hal berbeda, tanpa pengulangan sama sekali, cukup panjang."))
        assertNull(LoopGuard().check("Judul\n" + "-".repeat(120) + "\nIsi"))
    }

    @Test fun loopGuardDetectsRepeatedChunks() {
        val text = "Awal. " + "ulang terus tanpa henti! ".repeat(8)
        assertNotNull(LoopGuard().check(text))
    }

    // ---------------- Validasi header model ----------------

    private fun bigFile(head: ByteArray, suffix: String): File {
        val f = File.createTempFile("model", suffix)
        RandomAccessFile(f, "rw").use { it.write(head); it.setLength(60L * 1024 * 1024) }
        return f
    }

    private fun ggufHeader(tensors: Long): ByteArray {
        val out = ByteArrayOutputStream()
        fun u32(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun u64(v: Long) = out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array())
        fun str(s: String) { val b = s.toByteArray(); u64(b.size.toLong()); out.write(b) }
        out.write("GGUF".toByteArray()); u32(3); u64(tensors); u64(1)
        str("general.architecture"); u32(8); str("gemma4")
        return out.toByteArray()
    }

    @Test fun validatorAcceptsValidHeaders() {
        assertNull(ModelValidator.check(bigFile(ggufHeader(10), ".gguf").path, EngineType.GGUF))
        assertNull(ModelValidator.check(bigFile("LITERTLM".toByteArray() + ByteArray(8), ".litertlm").path, EngineType.LITERT))
        assertNull(ModelValidator.check(bigFile(byteArrayOf(0x50, 0x4B, 3, 4), ".task").path, EngineType.LITERT))
    }

    @Test fun validatorRejectsBadFiles() {
        assertTrue(ModelValidator.check(bigFile("<!DOCTYPE html><html>".toByteArray(), ".gguf").path, EngineType.GGUF)!!.contains("halaman web"))
        assertNotNull(ModelValidator.check(bigFile("NOTGGUF!".toByteArray(), ".gguf").path, EngineType.GGUF))
        assertNotNull(ModelValidator.check(bigFile(ggufHeader(0), ".gguf").path, EngineType.GGUF))
        assertNotNull(ModelValidator.check(bigFile("GGUF".toByteArray(), ".litertlm").path, EngineType.LITERT))
        val small = File.createTempFile("kecil", ".gguf").apply { writeBytes(ggufHeader(5)) }
        assertTrue(ModelValidator.check(small.path, EngineType.GGUF)!!.contains("terlalu kecil"))
    }

    // ---------------- Pemecahan dokumen ----------------

    @Test fun chunksNeverExceed4000() {
        val text = (1..400).joinToString("\n") { "Paragraf nomor $it " + "kata ".repeat(40) } + "\n" + "x".repeat(9000)
        val chunks = DocumentReader.chunk(text, 10_000)
        assertTrue(chunks.size > 3)
        assertTrue(chunks.all { it.length <= DocumentReader.MAX_CHUNK_CHARS + 1 })
    }

    // ---------------- Baris chat (anti-lag) ----------------

    private fun user(id: Long, t: String) = MessageEntity(id = id, sessionId = 1, role = Roles.USER, text = t)
    private fun ai(id: Long, t: String) = MessageEntity(id = id, sessionId = 1, role = Roles.ASSISTANT, text = t, kind = Kinds.TEXT)

    @Test fun rowsSplitAnswerIntoBlocksWithUniqueKeys() {
        val b = ChatRowsBuilder()
        val rows = b.build(listOf(user(1, "Hai"), ai(2, "# Judul\n\nParagraf satu.\n\n- poin\n\n```kotlin\nval x = 1\n```")), null)
        assertTrue(rows[0] is UserRow)
        assertTrue(rows[1] is AiHeaderRow)
        assertEquals(4, rows.count { it is AiBlockRow })
        assertTrue(rows.last() is AiFooterRow && (rows.last() as AiFooterRow).isLast)
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test fun streamingOnlyChangesLastBlock() {
        val b = ChatRowsBuilder()
        val msgs = listOf(user(1, "Hai"), ai(2, ""))
        val r1 = b.build(msgs, StreamState(2, text = "Paragraf satu.\n\nParagraf dua sedang"))
        val r2 = b.build(msgs, StreamState(2, text = "Paragraf satu.\n\nParagraf dua sedang ditulis"))
        val blocks1 = r1.filterIsInstance<AiBlockRow>()
        val blocks2 = r2.filterIsInstance<AiBlockRow>()
        assertEquals(blocks1[0], blocks2[0])                 // blok pertama sama → Compose melewatinya
        assertFalse(blocks1[1] == blocks2[1])                // hanya blok terakhir yang berubah
        assertTrue(r2.last() is AiLiveRow)
        assertTrue((r2[1] as AiHeaderRow).live)
    }

    @Test fun completedMessagesReuseCachedBlocks() {
        val b = ChatRowsBuilder()
        val msgs = listOf(user(1, "Hai"), ai(2, "A\n\nB"))
        val a = b.build(msgs, null).filterIsInstance<AiBlockRow>()
        val c = b.build(msgs, null).filterIsInstance<AiBlockRow>()
        assertSame(a[0].block, c[0].block)
    }

    @Test fun noFlickerBetweenStreamEndAndDatabaseUpdate() {
        val b = ChatRowsBuilder()
        b.build(listOf(user(1, "Hai"), ai(2, "")), StreamState(2, text = "Jawaban akhir"))
        // Stream sudah selesai, database belum terbarui (teks masih kosong) → tetap tampilkan teks terakhir.
        val rows = b.build(listOf(user(1, "Hai"), ai(2, "")), null)
        assertEquals(1, rows.count { it is AiBlockRow })
    }
}
