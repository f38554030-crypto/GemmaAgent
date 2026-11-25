package com.gemmaagent.app

import com.gemmaagent.app.chat.MemoryExtractor
import com.gemmaagent.app.llm.ThoughtSplitter
import com.gemmaagent.app.tools.Calculator
import com.gemmaagent.app.ui.MarkdownParser
import com.gemmaagent.app.ui.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test fun calculatorBasics() {
        assertEquals("2.500", Calculator.tryHandle("(12,5*4)^2")?.value)
        assertEquals("7", Calculator.tryHandle("1+2*3")?.value)
        assertEquals("2,5", Calculator.tryHandle("5/2")?.value)
        assertEquals("1.000.000", Calculator.tryHandle("1000*1000")?.value)
        assertEquals("12", Calculator.tryHandle("hitung sqrt(144)")?.value)
        assertEquals("15", Calculator.tryHandle("10% * 150")?.value)
        assertEquals("-3", Calculator.tryHandle("hitung 2-5")?.value)
    }

    @Test fun calculatorIgnoresNormalText() {
        assertNull(Calculator.tryHandle("Halo apa kabar?"))
        assertNull(Calculator.tryHandle("0812-3456-7890"))
        assertNull(Calculator.tryHandle("2024"))
        assertNull(Calculator.tryHandle("Jelaskan 3 langkah membuat kopi"))
    }

    @Test fun converter() {
        assertEquals("6,21371192237 mil", Calculator.tryHandle("10 km ke mil")?.value)
        assertEquals("212 f", Calculator.tryHandle("100 c ke f")?.value)
        assertEquals("1.024 mb", Calculator.tryHandle("1 gb ke mb")?.value)
        assertNull(Calculator.tryHandle("10 km ke kg"))
    }

    @Test fun calculatorDivideByZero() {
        assertNull(Calculator.tryHandle("5/0"))
    }

    @Test fun memoryExtractor() {
        assertEquals(listOf("Nama pengguna: Budi"), MemoryExtractor.extract("Halo, nama saya Budi"))
        assertTrue(MemoryExtractor.extract("ingat bahwa saya alergi kacang").first().contains("alergi kacang"))
        assertTrue(MemoryExtractor.extract("Saya bekerja sebagai guru SMA").first().contains("guru SMA"))
        assertTrue(MemoryExtractor.extract("Apa ibu kota Jepang?").isEmpty())
    }

    @Test fun thoughtSplitter() {
        val (t, th) = ThoughtSplitter.split("<thought>hitung dulu</thought>Jawabannya 4")
        assertEquals("Jawabannya 4", t)
        assertEquals("hitung dulu", th)
        val (t2, th2) = ThoughtSplitter.split("Halo <think>sedang mikir")
        assertEquals("Halo ", t2)
        assertEquals("sedang mikir", th2)
    }

    @Test fun markdownParser() {
        val md = """
            # Judul
            Paragraf **tebal** dan *miring*.
            - satu
            - [x] tugas selesai
            1. pertama
            > kutipan
            ```kotlin
            val x = 1
            ```
            | A | B |
            |---|---|
            | 1 | 2 |
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        assertTrue(blocks[2] is MdBlock.ListItem)
        assertTrue(blocks[3] is MdBlock.Task && (blocks[3] as MdBlock.Task).done)
        assertEquals("1.", (blocks[4] as MdBlock.ListItem).marker)
        assertTrue(blocks[5] is MdBlock.Quote)
        assertEquals("val x = 1", (blocks[6] as MdBlock.Code).code)
        assertEquals(2, (blocks[7] as MdBlock.Table).rows.size)
    }
}
