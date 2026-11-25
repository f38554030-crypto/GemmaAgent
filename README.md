# GemmaChat Hybrid — Asisten AI Offline + Online untuk Android

Chatbot AI hibrida untuk **POCO M4 Pro** (RAM 8 GB, Helio G96, AMOLED):
- **Offline 100%** dengan **Gemma 4 E2B IT (`.litertlm`)** lewat **LiteRT-LM** (backend **CPU**, 4–6 thread).
- **Online opsional** (ikon 🌐): DuckDuckGo HTML, Wikipedia API, atau Google News RSS — tanpa API key.
- Tanpa izin invasif: tidak ada Accessibility Service, overlay, foreground service, getar otomatis, atau TTS otomatis.

## Mengunduh APK
1. Tab **Actions** → workflow **Build APK** → tunggu centang hijau.
2. **Releases** → `apk-terbaru` → `GemmaChatHybrid.apk` → instal.

## Langkah pertama
1. Unduh `gemma-4-E2B-it.litertlm` (±2,59 GB) ke folder **Download**.
2. Buka aplikasi → Tutorial 3 layar → **Pengaturan → Model** → **Izinkan Akses Semua File**.
   Model terdeteksi otomatis dan dimuat **langsung dari Download (zero-copy)**.
   Cadangan: "Pilih manual (salin)".

## Fitur (PRD 50 fitur)
| Kelompok | Fitur |
|---|---|
| Hybrid & Web | Sakelar Online/Offline, pencarian tanpa API key, filter sumber (Web/Wikipedia/Berita), perangkum URL, kartu sitasi, sakelar Thinking |
| Multimodal & Dokumen | Gambar (kamera/galeri, vision), PDF/TXT, chunking hemat RAM, Tempel & Ringkas/Terjemahkan, kamus kata (tahan kata di jawaban) |
| Memori & Personalisasi | Memori jangka panjang (Room), manajer memori, 7 persona, system prompt kustom, template prompt, pemoles gaya bahasa |
| Belajar & Produktivitas | Kuis & flashcard, checklist interaktif, Mode Banding (Cepat vs Mendalam), kalkulator & konverter lokal, catatan |
| Percakapan & Data | Sidebar riwayat (cari, sematkan, ganti judul, hapus), auto-save draf, pesan berbintang, multi-model, ekspor .md/.txt, kartu kutipan gambar, backup/restore JSON |
| Mesin & Telemetri | Temperature 0,1–0,9, batas konteks & panjang jawaban 512/1536/3072, token/detik, RAM & suhu baterai, pembersih cache, statistik, peringatan suhu/istirahat, Stop instan |
| Interaksi | Edit & kirim ulang, regenerasi, salin & Copy Code, input suara, TTS manual (ikon speaker) |
| UI & Keamanan | AMOLED + 4 aksen, Markdown & tabel & pewarnaan sintaks, saran lanjutan, ukuran huruf, Zen Mode, onboarding, App Shortcuts, kunci biometrik |

## Catatan jujur
- Kecepatan token/detik adalah **perkiraan** (jumlah potongan teks streaming per detik).
- Pencarian web memakai scraping HTML; jika situs mengubah tampilan/memblokir, hasil bisa kosong (aplikasi otomatis jatuh ke Wikipedia lalu offline).
- Input gambar butuh model yang mendukung vision; mesin dimuat ulang saat gambar pertama dikirim (butuh RAM lebih).
