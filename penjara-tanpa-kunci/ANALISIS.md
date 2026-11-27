# Analisis & Perbaikan — *Penjara Tanpa Kunci* (v3 → v4)

Sumber: `Penjara_Tanpa_Kunci_v3_final.mp4` (Google Drive), 1080×1920, 30 fps, 190,8 detik, H.264 1,66 Mbps + AAC 48 kHz stereo, dibuat dengan Remotion 4.0.547.
Source code Remotion v3 **tidak tersedia**, jadi semua perbaikan dikerjakan langsung pada hasil render (perbaikan piksel + overlay Remotion baru di atasnya).

## Cara analisis
- Teknis: `ffprobe`, deteksi frame hitam/freeze, pergantian scene, loudness EBU R128, clipping.
- Visual: 191 frame (1/detik) + ±150 frame detail resolusi penuh di titik rawan.
- Narasi: transkrip Whisper large-v3 (timestamp per kata) + Silero VAD.
- Caption: OCR tiap 0,2 detik, dibandingkan dengan narasi (teks + sinkron karaoke).
- Data per frame: OCR angka HUD, pelacakan kalender, deteksi geometri kubus.

## Yang sudah benar (tidak diubah)
- Audio: loudness −14,2 LUFS (pas untuk TikTok/Reels/Shorts), true peak −1,4 dBTP, tanpa clipping, kanal kiri-kanan seimbang. Dua jeda sunyi ±1 detik (2:01 dan 2:10) jatuh di jeda dramatis → dipertahankan.
- Caption vs narasi: semua kata cocok, tidak ada salah ketik.
- Sinkron karaoke: rata-rata kata menyala 0,08 detik setelah diucapkan (dari 67 kata yang diukur).
- Alur cerita, metafora (janji "besok" → kebiasaan → penjara → pintu tak terkunci → langkah pertama), dan gaya visual konsisten.

## Daftar masalah

| # | Waktu | Masalah | Tingkat | Status v4 |
|---|---|---|---|---|
| 1 | 0:00–0:02 | **Hook lemah.** 1,7 detik pertama tanpa narasi. Satu-satunya teks cuma pil judul kecil yang **bertabrakan** dengan tepi atas ponsel dan garis kepala siluet. Frame pertama (thumbnail) tidak punya pemancing. | Mayor | ✅ Adegan pembuka dibuat ulang: judul + kalimat pemancing besar "412 kali ia berkata "besok"." sudah terbaca dari frame 0. Caption asli tetap dipakai, jadi sambungan ke adegan kamar mulus. |
| 2 | 0:21,5–0:31 | **Kalender berkedip.** Saat montase waktu, halaman yang disobek selalu **kosong** dan angka cuma "mengintip" dari halaman di bawahnya. Akibatnya kalender kosong/berkedip ±5× per detik. Logikanya juga terbalik: di kalender sobek, halaman yang disobek justru membawa angka hari itu. | Mayor | ✅ Halaman yang jatuh sekarang membawa angka hari sebelumnya. Posisi dan rotasinya dipasang per frame dari video asli (fitting IoU 0,95–1,00). |
| 3 | 1:18–1:40, 2:44–2:54, 3:07–3:10 | **Bayangan kubus "tembus".** Bayangan gelap transparan (12,5%) jatuh ke belakang-kanan dan menimpa pohon dan gedung di latar, sehingga terlihat seperti panel kaca. Secara logika bayangan hanya boleh jatuh di trotoar. | Mayor | ✅ Bayangan di atas trotoar dihapus secara matematis di ruang YUV (piksel ÷ 0,875). Tepinya diukur sub-piksel per frame; bayangan di trotoar tetap ada. 600 frame diproses. |
| 4 | 3:06,7–3:07,5 | **Payoff hilang.** "LANGKAH NYATA 1", balasan visual dari "JANJI "BESOK" 412", cuma tampil **0,8 detik**. | Mayor | ✅ Tetap tampil sepanjang shot kaki. Saat cut ke adegan penutup, kotaknya pindah ke bawah kartu penutup dan berdampingan dengan "JANJI "BESOK" ~~412~~". |
| 5 | 0:08–2:24 | **Label penghitung tidak terbaca di HP.** Tulisan "JANJI "BESOK"" cuma ±21 px (setara ±7 pt di layar HP), padahal ini alat cerita utama. | Sedang | ✅ HUD diganti versi lebih besar dengan gaya identik (label 30 px, angka 76 px). Nilai per frame dibaca dari video asli via OCR (324 nilai unik). |
| 6 | 0:38,3–0:42,5 | **Gelembung pikiran menutup separuh angka kalender** ("367" kepotong jadi "3б7"). | Sedang | ✅ Kalender disembunyikan halus (fade) selama gelembung tampil, lalu muncul lagi. |
| 7 | 2:33,8–2:36,4 | **Caption menyatu dengan palang penjara.** Palang hitam lewat tepat di belakang kotak caption yang warnanya hampir sama. | Sedang | ✅ Kotak caption di adegan itu diberi garis tepi terang tipis. |
| 8 | 2:24,2–2:24,3 (4 frame) | **Teks "TIDAK ADA JANJI BESOK" muncul di luar kartunya** (teks "hantu" di atas catatan kuning). | Minor | ✅ Dihapus di 4326–4329 (lihat bagian QA). |
| 9 | 0:10 | Tanda baca: "Namun ketika esok tiba," tanpa koma. Dua "Namun," lainnya pakai koma (PUEBI). | Minor | ⚠️ Tidak diubah. Caption sudah "terbakar" di video; mengganti satu koma butuh render ulang caption, dan risiko tampilannya beda lebih besar daripada manfaatnya. |
| 10 | 1:13, 2:59 | Koma antara subjek–predikat ("…hanya kebiasaan, mulai…") dan sebelum anak kalimat ("…kehidupan, selama…"). | Minor | ⚠️ Tidak diubah. Masih wajar sebagai penanda jeda narasi. |
| 11 | 1:18–1:36 | Komposisi adegan kota: tokoh kecil (5–15% tinggi frame), ±35% bawah frame kosong (jalan/trotoar). | Minor | ⚠️ Tidak diubah. Butuh membangun ulang adegan, dan source tidak ada. |
| 12 | 1:39 | Gambar "pengalaman" di gelembung atas kubus terlalu kecil/kurang jelas. | Minor | ⚠️ Tidak diubah (alasan sama). |
| 13 | 0:28–0:31 | Catatan tempel meluber keluar dinding kamar saat puncak montase. | Kosmetik | ⚠️ Dibiarkan. Terbaca sebagai efek "meluap" yang disengaja. |
| 14 | 1:53 | Kalender terpotong di tepi frame saat kamera menyorot ventilasi. | Kosmetik | ⚠️ Dibiarkan (framing kamera). |

## Pipeline perbaikan
1. `tools/` (Python): analisis, lalu perbaikan piksel (`repair_base.py`): bayangan kubus, kalender di balik gelembung, teks hantu. Piksel lain tetap identik bit-per-bit dengan v3 (diproses di YUV420 langsung).
2. `remotion/` (Remotion 4.0.547): video hasil perbaikan jadi dasar, lalu ditimpa overlay: hook baru, HUD besar, angka kalender, garis tepi caption, payoff.
3. Audio asli v3 dipasang ulang tanpa re-encode (stream copy).

## QA independen (1 putaran) dan tindak lanjutnya

Reviewer terpisah memeriksa v4 frame per frame (selisih v3 vs v4 di seluruh 5.724 frame, OCR digit HUD di kedua file, detektor halaman kosong, perbandingan bidang YUV, crop 3× di setiap area yang diperbaiki).
Hasilnya: tidak ada blocker atau temuan mayor; 7 minor dan 6 nit.

| Temuan QA | Tindak lanjut |
|---|---|
| Kalender masih kosong 17 frame (siklus tanpa perubahan angka, misalnya 7→7 dan 366→366) | ✅ Halaman yang disobek **selalu** membawa angka. Sekarang 0 frame kosong. |
| Tepi gelembung pikiran penyok/bergelombang di bekas area kalender | ✅ Tepi dibangun ulang dari **circle fit** per lekukan awan, dengan anti-aliasing analitik. |
| Teks hantu masih terlihat di 4327–4329, bergerigi di 4330 | ✅ Frame 4326–4329: catatan kuning dipulihkan dari frame referensi 4325 (catatannya statis). Hanya piksel teks yang diganti; bayangan dan bingkai kartu tetap. ⚠️ Frame 4330–4332 (0,1 detik) dibiarkan seperti v3: teks sedang "masuk" ke kartu yang melebar dan menyeberangi tepinya. Memotongnya justru menghasilkan huruf bergerigi. |
| HUD besar menabrak kalender (13,3–14,0 s dan 30,2–31,0 s) | ✅ HUD dipindah ke x ≥ 700 (lebar 314), masih menutup HUD lama 100%. |
| Bayangan kubus muncul lagi di akhir shot kota pertama (2506–2538, saat pan) | ✅ Tracking khusus: tepi kanan kubus, lalu tepi bayangan itu sendiri, sampai cut di 2538. |
| Garis terang 5653–5659 | ✅ Pengukuran mentah yang menyimpang ditolak, lalu geometri dihaluskan secara robust. Saat kamera bergerak cepat (>4 px/frame), nilai mentah yang akurat tetap dipakai. |
| Bayangan kembali 1 frame di 3008 | ✅ Segmen diperpanjang sampai 3008. |
| HUD lama tembus saat fade-in (247–249) | ✅ HUD baru langsung opaque dari 247 dengan efek pop kecil. |
| Garis tepi caption terbawa 3 frame ke shot lebar | ✅ Berhenti di 4688 (frame cut). |
| Payoff: kotak melintasi kartu penutup; HUD lama terlihat 1 frame di 5599 | ✅ Overlay mulai 5599; kotak pindah ke bawah kartu tepat saat cut (5637). |
| Pil judul hook di zona UI atas TikTok/Reels | ✅ Diturunkan ke y ≥ 262. |
| Serah terima HUD (dari QA dinyatakan sudah benar) | ✅ Disempurnakan: HUD baru "morph" ke tata letak HUD lama (ukuran kotak, font, posisi) sebelum HUD lama terbang ke tengah. |
| Garis tepi 1 px yang hanya terlihat setelah kontras dinaikkan | ⚠️ Dibiarkan (di bawah ambang terlihat). |
| Ponsel lama samar di dalam pil caption hook (hanya terlihat ×4 kecerahan) | ⚠️ Dibiarkan. Sama dengan transparansi 92% pil caption di seluruh video asli. |
| Angka halaman yang jatuh ≠ angka HUD di frame yang sama | ⚠️ Disengaja. Halaman yang disobek = hari sebelumnya, HUD = hitungan saat itu. |
