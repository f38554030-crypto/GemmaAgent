package com.gemmaagent.app

import com.gemmaagent.app.data.GgufReader
import com.gemmaagent.app.data.ModelFile
import com.gemmaagent.app.data.ModelRepository
import com.gemmaagent.app.data.ModelSource
import com.gemmaagent.app.data.kb.KnowledgeDb
import com.gemmaagent.app.llm.ChatTemplate
import com.gemmaagent.app.llm.ChatTurn
import com.gemmaagent.app.llm.EngineType
import com.gemmaagent.app.llm.ModelSettings
import com.gemmaagent.app.llm.PromptChains
import com.gemmaagent.app.llm.PromptFormatter
import com.gemmaagent.app.ui.CodePreview
import com.gemmaagent.app.ui.DiffOp
import com.gemmaagent.app.ui.TextDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HubLogicTest {

    private fun ggufFile(): File {
        val out = ByteArrayOutputStream()
        fun u32(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun u64(v: Long) = out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array())
        fun str(s: String) { val b = s.toByteArray(); u64(b.size.toLong()); out.write(b) }
        out.write("GGUF".toByteArray())
        u32(3)            // versi
        u64(0)            // jumlah tensor
        u64(5)            // jumlah KV
        str("general.architecture"); u32(8); str("gemma4")
        str("general.file_type"); u32(4); u32(15)
        str("tokenizer.ggml.tokens"); u32(9); u32(8); u64(3); str("<bos>"); str("halo"); str("dunia")
        str("gemma4.context_length"); u32(4); u32(131072)
        str("tokenizer.chat_template"); u32(8); str("{{ bos_token }}<start_of_turn>user")
        val f = File.createTempFile("tes", ".gguf")
        f.writeBytes(out.toByteArray())
        return f
    }

    @Test fun ggufHeader() {
        val md = GgufReader.read(ggufFile())
        assertEquals("gemma4", md.architecture)
        assertEquals("Q4_K_M", md.quantization)
        assertEquals(131072L, md.contextLength)
        assertTrue(md.chatTemplate!!.contains("<start_of_turn>"))
        assertEquals(3, md.ggufVersion)
    }

    @Test fun gemmaTemplateMergesSystem() {
        val p = PromptFormatter.format(ChatTemplate.GEMMA, "Kamu ramah.", listOf(ChatTurn(true, "Hai"), ChatTurn(false, "Halo!")), "Apa kabar?")
        assertEquals(
            "<start_of_turn>user\nKamu ramah.\n\nHai<end_of_turn>\n<start_of_turn>model\nHalo!<end_of_turn>\n" +
                "<start_of_turn>user\nApa kabar?<end_of_turn>\n<start_of_turn>model\n",
            p,
        )
    }

    @Test fun chatmlTemplate() {
        val p = PromptFormatter.format(ChatTemplate.CHATML, "S", emptyList(), "U")
        assertEquals("<|im_start|>system\nS<|im_end|>\n<|im_start|>user\nU<|im_end|>\n<|im_start|>assistant\n", p)
    }

    @Test fun modelTypeAndLabel() {
        val g = ModelFile("/x/gemma4-latih-Q4_K_M.gguf", "gemma4-latih-Q4_K_M.gguf", 3_426_660_448, ModelSource.DOWNLOAD_DIRECT)
        assertEquals(EngineType.GGUF, g.type)
        assertEquals("Gemma 4 Q4_K_M", g.shortLabel)
        val l = ModelFile("/x/gemma-4-E2B-it.litertlm", "gemma-4-E2B-it.litertlm", 2_590_000_000, ModelSource.DOWNLOAD_DIRECT)
        assertEquals(EngineType.LITERT, l.type)
        assertEquals("Gemma 4 E2B", l.shortLabel)
    }

    @Test fun modelSettingsRoundTrip() {
        val s = ModelSettings(template = ChatTemplate.CHATML, temperature = 0.4f, repeatPenalty = 1.2f, topP = 0.8f, nCtx = 3072, stopWords = listOf("###"))
        assertEquals(s, ModelSettings.fromJson(s.toJson()))
    }

    @Test fun driveIds() {
        assertEquals("1vKHZZIx9yrU4QQ4WdvLXV4vgyYkKLNEY", ModelRepository.extractDriveId("https://drive.google.com/file/d/1vKHZZIx9yrU4QQ4WdvLXV4vgyYkKLNEY/view?usp=drivesdk"))
        assertEquals("1vKHZZIx9yrU4QQ4WdvLXV4vgyYkKLNEY", ModelRepository.extractDriveId("https://drive.google.com/open?id=1vKHZZIx9yrU4QQ4WdvLXV4vgyYkKLNEY"))
        assertNull(ModelRepository.extractDriveId("https://drive.google.com/drive/folders/1rv8D1k-5xcaXvYZ8QbrMyV7eGLqs6NxL"))
    }

    @Test fun diff() {
        val ops = TextDiff.words("kucing makan ikan", "kucing makan ayam goreng")
        assertEquals(listOf(DiffOp.SAME, DiffOp.SAME, DiffOp.DEL, DiffOp.ADD, DiffOp.ADD), ops.map { it.first })
    }

    @Test fun codePreviewKinds() {
        assertEquals("html", CodePreview.kind("html", "<b>x</b>"))
        assertEquals("mermaid", CodePreview.kind("mermaid", "graph TD; A-->B"))
        assertEquals("json", CodePreview.kind("json", "{}"))
        assertNull(CodePreview.kind("kotlin", "val x = 1"))
    }

    @Test fun ftsQuery() {
        assertEquals("harga* emas* hari*", KnowledgeDb.ftsQuery("berapa harga emas hari ini?")!!.replace(" OR ", " "))
        assertNull(KnowledgeDb.ftsQuery("apa itu?"))
    }

    @Test fun customChain() {
        val c = PromptChains.custom("Ringkas: {input}\n---\nJudul dari: {prev}")
        assertEquals(2, c.steps.size)
        assertEquals("Judul dari: {prev}", c.steps[1].second)
    }
}
