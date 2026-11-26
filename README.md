# GemmaChat Hybrid — PocketModel Hub & General Web Search (POCO M4 Pro Edition)

Chatbot AI hibrida untuk Android (Kotlin + Jetpack Compose, Material 3):
- **Dual-engine `UnifiedLlmEngine`**: **llama.cpp** (NDK/CMake, arm64-v8a, NEON + DOTPROD, `mmap`) untuk **`.gguf`**, dan **LiteRT-LM** (CPU) untuk **`.litertlm` / `.task`**.
- **Strict Unload-Before-Load** saat ganti model.
- **Pencarian web umum tanpa API key** (DuckDuckGo HTML + Wikipedia API, opsional Google News RSS) + pembaca artikel + kartu sitasi + arsip offline.
- **UI ala Gemini** (Dark AMOLED `#0E0E10` / Clean Light `#F8F9FA`), **Smart Scroll Lock** + tombol "↓ Ke bawah", token buffering 50 ms.
- Bersih dari fitur agen: tanpa Accessibility Service, overlay, tombol melayang, WhatsApp, getar/TTS otomatis.

## Versi 3.1 — audit performa & UI flagship
- **Anti-patah-patah**: jawaban AI dipecah per blok di LazyColumn (key unik, `@Immutable`), parsing Markdown/JSON di thread latar, token buffering 50 ms → saat streaming hanya blok terbawah yang dirender ulang.
- **Smart Scroll Lock**: sentuhan jari langsung mengunci gulir otomatis; tombol kapsul "↓ Ke bawah".
- **Anti-OOM**: gambar maks 1024×1024 (EXIF) untuk OCR & thumbnail, PDF dibaca per halaman, potongan teks maks 4.000 karakter.
- **Stabilitas mesin**: pembersih token teknis (`<end_of_turn>`, `<|im_end|>`, `<|eot_id|>`, `</s>`, …), penjaga anti-looping, validasi header GGUF/LITERTLM sebelum load.
- **Unduhan Drive** lewat WorkManager → `.tmp` → rename setelah 100%; web search timeout 8 detik + fallback Wikipedia.
- **UI Gemini/Perplexity**: layar awal grid 2×2, jawaban AI menyatu dengan latar `#0E0E10`, karusel sumber web, input kapsul dengan border glow saat Online, pemilih model (bottom sheet) dengan meteran RAM.
- APK kini **build rilis non-debuggable** (lebih mulus daripada build debug).

## Unduh APK
**Releases → `apk-terbaru` → `GemmaChatHybrid.apk`** (dibangun otomatis oleh GitHub Actions setiap push).

## Memakai model GGUF hasil latih ulang dari Google Drive
1. Buka aplikasi → **Model Hub** (ketuk nama model di bilah atas, atau menu ☰).
2. **Izinkan Akses Semua File**.
3. **Unduh dari Drive** → pilih `gemma4-latih-Q4_K_M.gguf (3,43 GB)` — diunduh ke `Download/GemmaModels/`
   (file Drive harus dibagikan "Siapa saja yang memiliki link"). Atau unduh manual lalu ketuk **Pindai Ulang**.
4. Ketuk **Muat / Pakai** pada kartu model. Atur template/temperature/n_ctx di **Pengaturan Model** bila perlu.

File yang dipilih lewat File Manager / ZArchiver dibaca langsung (zero-copy); file dari Google Drive (cloud) disalin di latar oleh WorkManager (buffer 64 KB).

## Build lokal
```bash
git clone --recurse-submodules <repo>
./gradlew assembleDebug   # butuh NDK 28.2.13676358 + CMake 3.31.6
```
llama.cpp disertakan sebagai submodule `app/src/main/cpp/llama.cpp` (dikunci di tag `b12767`).

## Catatan jujur
- Belum diuji di perangkat fisik; kecepatan nyata di Helio G96 belum diukur (gunakan tombol **Benchmark** di Model Hub).
- Pencarian web memakai scraping HTML: bisa berhenti bekerja bila situs mengubah tampilan/memblokir.
- RAG memakai **SQLite FTS4** (FTS5 tidak dijamin tersedia di semua HP Android).
- OCR memakai ML Kit (model offline di APK); library Google ini dapat mengirim statistik pemakaian anonim saat online.
- Pratinjau Mermaid memakai `mermaid.min.js` (MIT) yang dibundel di APK.
