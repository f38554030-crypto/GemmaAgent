# Gemma Agent — Agen AI Lokal Offline untuk Android

Aplikasi chat agen AI yang berjalan **100% offline** di HP (dioptimalkan untuk POCO M4 Pro, RAM 8 GB, MediaTek),
memakai model **Gemma 4 E2B IT (`.litertlm`)** lewat **LiteRT-LM** (penerus resmi MediaPipe LLM Inference).

## Cara mendapatkan APK (tanpa koding)

1. Buka tab **Actions** di repositori ini → pilih workflow **Build APK** → tunggu sampai centang hijau (±10–15 menit).
2. Unduh APK dengan salah satu cara:
   - **Paling mudah:** buka halaman **Releases** → `apk-terbaru` → ketuk `GemmaAgent.apk`.
   - Atau: Actions → klik run terakhir → bagian **Artifacts** → `GemmaAgent-APK` (berbentuk .zip, ekstrak dulu).
3. Instal APK di HP (izinkan "Instal dari sumber tidak dikenal").

Build bisa dijalankan ulang manual: Actions → Build APK → **Run workflow**.

## Siapkan model

Unduh file **Gemma 4 E2B IT** berformat `.litertlm` (±2,6 GB) dari Hugging Face
(organisasi `litert-community`) ke folder **Download** di HP. Butuh ruang kosong ±3 GB lagi untuk salinan internal.

## Langkah pertama di aplikasi (Beranda)

1. **Pilih File Model** → pilih file `.litertlm` → tunggu progress bar 100% (disalin sekali saja).
2. **Izin Tampil di Atas Aplikasi Lain** → aktifkan untuk Gemma Agent.
3. **Layanan Aksesibilitas** → aktifkan "Gemma Agent (Kontrol Layar AI)".
   - Kalau tombolnya abu-abu / muncul "Setelan terbatas" (Android 13+): Info Aplikasi → menu ⋮ → **Izinkan setelan terbatas**.
4. Tombol **Mulai Chat Agen** aktif setelah ketiganya centang hijau.

Tips MIUI/HyperOS: set Penghemat baterai = **Tanpa batasan**, aktifkan **Mulai otomatis**, dan kunci aplikasi di menu Terbaru.

## Fitur

| Fitur | Keterangan |
|---|---|
| Model lokal | LiteRT-LM, **backend CPU**, `maxNumTokens = 1536` |
| Foreground Service | Notifikasi aktif + tombol STOP AGEN di notifikasi |
| Accessibility Service | Membaca teks layar; aksi JSON `click_text`, `type_text`, `swipe`, `open_app`, `back`, `home` (+ `open_notifications`, `done`) |
| Floating Pill | Kapsul transparan, titik berkedip saat berpikir, status 1 baris, tombol merah STOP; ketuk teks untuk mini-panel log; bisa digeser |
| Log pemikiran real-time | Di layar chat & mini-panel melayang |
| Anti-looping | Maks. 15 langkah/perintah, jeda 1,5 detik, berhenti jika aksi sama diulang 3x |
| Safety Guard | Pop-up "Izinkan/Tolak" untuk tombol Transfer, Bayar, Kirim, Hapus, Beli (dll.) |
| UI | Material 3, Jetpack Compose, dark mode AMOLED (hitam pekat + biru elektrik/ungu) |
| Chat | Gelembung kanan/kiri, kartu ringkasan aksi, typing dots, quick action chips |
| Top bar | Monitor RAM real-time + status model ("Gemma 4 E2B • Siap"), tombol Sesi Baru / Reset Memori |
| Input suara | SpeechRecognizer bawaan Android (id-ID) |
| Umpan balik | Haptic saat tombol utama/STOP, TTS + beep saat tugas selesai |

## Batasan yang perlu diketahui

- Konteks 1536 token itu kecil: teks layar dipotong ±1300 karakter per langkah, dan memori percakapan hanya menyimpan 3 perintah terakhir.
- Model 2B bisa salah memilih tombol. Untuk aksi berisiko tetap ada konfirmasi, tapi selalu awasi agen.
- Kecepatan di CPU Helio G96 diperkirakan beberapa detik per langkah (belum diukur di perangkat).
