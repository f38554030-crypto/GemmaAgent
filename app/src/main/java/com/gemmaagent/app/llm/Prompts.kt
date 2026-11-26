package com.gemmaagent.app.llm

/** Persona / peran AI (fitur 14). */
data class Persona(val id: String, val name: String, val emoji: String, val prompt: String)

object Personas {
    val all = listOf(
        Persona("umum", "Asisten Umum", "✨", "Kamu adalah asisten AI yang ramah, jujur, dan membantu. Jawab dengan jelas dan ringkas."),
        Persona(
            "penerjemah", "Penerjemah 2 Arah", "🌐",
            "Kamu adalah penerjemah profesional. Jika teks pengguna berbahasa Indonesia, terjemahkan ke bahasa Inggris; jika bahasa lain, terjemahkan ke bahasa Indonesia. Berikan hanya terjemahan yang natural, lalu catatan singkat bila ada istilah penting.",
        ),
        Persona(
            "mentor", "Mentor Belajar", "🎓",
            "Kamu adalah mentor belajar yang sabar. Jelaskan konsep langkah demi langkah, beri contoh, lalu ajukan satu pertanyaan singkat untuk mengecek pemahaman.",
        ),
        Persona("penulis", "Penulis Kreatif", "🖋️", "Kamu adalah penulis kreatif. Tulis dengan gaya hidup, imajinatif, dan enak dibaca, tetap sesuai permintaan."),
        Persona("ringkas", "Ringkas Poin", "📌", "Jawab SELALU dalam poin-poin singkat (maksimal 6 poin), tanpa basa-basi."),
        Persona(
            "eli5", "Mode ELI5", "🧸",
            "Jelaskan seolah kepada anak usia 10 tahun: kalimat pendek, kata sederhana, dan analogi sehari-hari.",
        ),
        Persona(
            "tatabahasa", "Pemeriksa Tata Bahasa", "✅",
            "Kamu adalah editor bahasa. Perbaiki ejaan, tanda baca, dan tata bahasa teks pengguna. Tampilkan versi perbaikan, lalu daftar singkat perubahan utama.",
        ),
    )

    fun byId(id: String): Persona = all.firstOrNull { it.id == id } ?: all.first()
}

/** Pustaka template prompt siap pakai (fitur 16). */
data class PromptTemplate(val title: String, val emoji: String, val text: String)

object PromptTemplates {
    val all = listOf(
        PromptTemplate("Draf Email", "📧", "Buatkan draf email profesional kepada [penerima] tentang [topik]. Nada: sopan dan jelas. Poin penting:\n- "),
        PromptTemplate("Rapikan Catatan Rapat", "📝", "Rapikan catatan rapat berikut menjadi: Ringkasan, Keputusan, Tugas (siapa–apa–kapan).\n\nCatatan:\n"),
        PromptTemplate("Kerangka Konten", "🧱", "Buat kerangka konten tentang [topik] untuk [platform/audiens]: judul menarik, 5 subjudul, dan poin utama tiap subjudul."),
        PromptTemplate("Rencana Harian", "📅", "Susun rencana harian realistis untuk hari ini. Prioritas saya: [daftar]. Waktu luang: [jam]. Sertakan jeda istirahat."),
        PromptTemplate("Jelaskan Konsep", "💡", "Jelaskan konsep [konsep] dengan bahasa sederhana, beri 1 analogi dan 1 contoh nyata."),
        PromptTemplate("Pro & Kontra", "⚖️", "Buat tabel pro dan kontra untuk keputusan: [keputusan]. Akhiri dengan rekomendasi singkat."),
        PromptTemplate("Ide Caption", "📣", "Beri 5 ide caption media sosial tentang [topik], gaya santai, maksimal 2 kalimat per caption."),
        PromptTemplate("Langkah-langkah", "🪜", "Tuliskan langkah-langkah bernomor untuk [tugas], singkat dan praktis."),
    )
}

/** Rantai Prompt (Prompt Chaining): langkah berurutan; {input} = teks awal, {prev} = hasil langkah sebelumnya. */
data class PromptChain(val title: String, val emoji: String, val steps: List<Pair<String, String>>)

object PromptChains {
    val all = listOf(
        PromptChain("Catatan → Aksi → Email", "📨", listOf(
            "Ringkas" to "Ringkas teks berikut dalam 5 poin penting:\n\n{input}",
            "Daftar tugas" to "Dari ringkasan ini, buat daftar tugas bernomor (siapa/apa/kapan bila ada):\n\n{prev}",
            "Draf email" to "Tulis email singkat dan sopan untuk tim berisi daftar tugas berikut:\n\n{prev}",
        )),
        PromptChain("Riset → Kerangka → Artikel", "📰", listOf(
            "Poin kunci" to "Sebutkan 6 poin kunci tentang topik: {input}",
            "Kerangka" to "Susun kerangka artikel dari poin berikut:\n\n{prev}",
            "Artikel" to "Tulis artikel ±300 kata berdasarkan kerangka ini:\n\n{prev}",
        )),
        PromptChain("Teks → Terjemah → Poles", "🌐", listOf(
            "Terjemahkan" to "Terjemahkan ke bahasa Inggris:\n\n{input}",
            "Poles" to "Perbaiki agar terdengar natural dan profesional:\n\n{prev}",
        )),
        PromptChain("Materi → Ringkas → Kuis", "🎓", listOf(
            "Ringkas" to "Ringkas materi berikut untuk belajar:\n\n{input}",
            "Pertanyaan latihan" to "Buat 5 pertanyaan latihan beserta jawabannya dari ringkasan ini:\n\n{prev}",
        )),
    )

    /** Rantai kustom: langkah dipisah baris "---". */
    fun custom(text: String): PromptChain = PromptChain(
        "Rantai kustom", "🔗",
        text.split(Regex("\\n-{3,}\\n")).map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { i, t -> "Langkah ${i + 1}" to t },
    )
}

/** Gaya pemoles teks (fitur 17). */
enum class PolishStyle(val label: String, val instruction: String) {
    FORMAL("Formal", "gaya formal dan profesional"),
    SANTAI("Santai", "gaya santai dan akrab"),
    PERSUASIF("Persuasif", "gaya persuasif yang meyakinkan"),
    SINGKAT("Singkat", "versi paling singkat tanpa menghilangkan makna"),
}

object PromptBuilder {

    fun systemPrompt(
        personaId: String,
        custom: String,
        thinking: Boolean,
        memories: List<String>,
        online: Boolean,
    ): String {
        val sb = StringBuilder()
        sb.append(Personas.byId(personaId).prompt).append('\n')
        if (custom.isNotBlank()) sb.append("Aturan tambahan dari pengguna:\n").append(custom.trim().take(800)).append('\n')
        if (thinking) {
            sb.append("Pikirkan masalah secara bertahap sebelum menjawab, lalu berikan jawaban akhir yang rapi.\n")
        } else {
            sb.append("Jawab langsung tanpa menuliskan proses berpikir atau blok <thought>.\n")
        }
        if (online) sb.append("Jika diberi HASIL PENCARIAN, gunakan sebagai sumber utama dan sebut nomor sumber seperti [1].\n")
        if (memories.isNotEmpty()) {
            sb.append("Fakta tentang pengguna (gunakan bila relevan):\n")
            memories.take(12).forEach { sb.append("- ").append(it.take(160)).append('\n') }
        }
        sb.append("Gunakan Markdown (judul, poin, **tebal**, tabel, blok kode) bila membantu. Bahasa default: Indonesia.")
        return sb.toString()
    }

    fun withWebContext(question: String, results: List<com.gemmaagent.app.web.WebResult>, budgetChars: Int, fromCache: Boolean = false): String {
        val sb = StringBuilder(if (fromCache) "HASIL PENCARIAN (arsip offline):\n" else "HASIL PENCARIAN WEB:\n")
        var used = 0
        results.forEachIndexed { i, r ->
            val body = r.content.ifBlank { r.snippet }
            val room = (budgetChars - used - r.title.length - 20).coerceAtLeast(0)
            if (room < 80) return@forEachIndexed
            val block = "[${i + 1}] ${r.title}\n${body.take(room)}\n"
            sb.append(block)
            used += block.length
        }
        sb.append("\nPERTANYAAN: ").append(question)
        return sb.toString()
    }

    fun withKnowledge(question: String, hits: List<com.gemmaagent.app.data.kb.KbHit>, budgetChars: Int): String {
        val sb = StringBuilder("KUTIPAN BASIS PENGETAHUAN PRIBADI:\n")
        var used = 0
        hits.forEach { h ->
            val block = "— (${h.docName}) ${h.text.trim()}\n"
            if (used + block.length > budgetChars) return@forEach
            sb.append(block)
            used += block.length
        }
        sb.append("\nJawab berdasarkan kutipan di atas bila relevan.\nPERTANYAAN: ").append(question)
        return sb.toString()
    }

    fun withOcr(question: String, ocrText: String): String =
        "TEKS DARI GAMBAR (hasil OCR):\n${ocrText.take(3000)}\n\nPERMINTAAN: ${question.ifBlank { "Jelaskan isi teks pada gambar ini." }}"

    fun withDocument(question: String, docName: String, docText: String): String =
        "DOKUMEN \"$docName\":\n$docText\n\nPERMINTAAN: ${question.ifBlank { "Rangkum dokumen ini dalam poin-poin penting." }}"

    fun chunkSummary(docName: String, part: Int, total: Int, chunk: String): String =
        "Ini bagian $part dari $total dokumen \"$docName\". Rangkum isi pentingnya dalam maksimal 6 poin singkat:\n\n$chunk"

    fun urlSummary(question: String, title: String, text: String): String =
        "ARTIKEL \"$title\":\n$text\n\nPERMINTAAN: ${question.ifBlank { "Rangkum artikel ini dalam poin-poin penting." }}"

    fun followUps(question: String, answer: String): String =
        "Pertanyaan: ${question.take(300)}\nJawaban: ${answer.take(700)}\n\n" +
            "Tuliskan tepat 3 pertanyaan lanjutan singkat (maks 10 kata) yang mungkin ditanyakan pengguna berikutnya. " +
            "Satu pertanyaan per baris, tanpa nomor, tanpa penjelasan."

    fun quiz(source: String): String =
        "Buat 4 soal pilihan ganda dari teks berikut. Balas HANYA JSON valid dengan format:\n" +
            "{\"questions\":[{\"q\":\"pertanyaan\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":0,\"explain\":\"alasan singkat\"}]}\n" +
            "(answer = indeks opsi benar, mulai 0)\n\nTEKS:\n${source.take(2500)}"

    fun flashcards(source: String): String =
        "Buat 6 flashcard hafalan dari teks berikut. Balas HANYA JSON valid dengan format:\n" +
            "{\"cards\":[{\"front\":\"istilah/pertanyaan\",\"back\":\"jawaban singkat\"}]}\n\nTEKS:\n${source.take(2500)}"

    fun define(word: String, sentence: String): String =
        "Jelaskan arti kata \"$word\" sesuai konteks kalimat: \"${sentence.take(300)}\".\n" +
            "Format:\nArti: (1 kalimat)\nSinonim: (2-4 kata)\nContoh: (1 kalimat)"

    fun polish(style: PolishStyle, text: String): String =
        "Tulis ulang teks berikut dengan ${style.instruction}. Berikan hanya hasilnya.\n\nTEKS:\n$text"

    fun clipboard(action: String, text: String): String = when (action) {
        "translate" -> "Terjemahkan teks berikut (Indonesia ↔ Inggris), berikan hanya terjemahannya:\n\n$text"
        else -> "Ringkas teks berikut dalam poin-poin penting:\n\n$text"
    }
}
