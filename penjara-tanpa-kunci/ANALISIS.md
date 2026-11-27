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
| 3 | 1:18–1:40, 2:44–2:54, 3:07–3:10 | **Bayangan kubus "tembus".** Bayangan gelap transparan (12,5%) jatuh ke belakang-kanan dan menimpa pohon dan gedung di latar, sehingga terlihat seperti panel kaca. Secara logika bayangan hanya boleh jatuh di trotoar. | Mayor | ✅ Bayangan di atas trotoar dihapus secara matematis di ruang YUV (piksel ÷ 0,875). Tepinya diukur sub-piksel per frame; bayangan di trotoar tetap ada. 582 frame diproses. |
| 4 | 3:06,7–3:07,5 | **Payoff hilang.** "LANGKAH NYATA 1", balasan visual dari "JANJI "BESOK" 412", cuma tampil **0,8 detik**. | Mayor | ✅ Tetap tampil sepanjang shot kaki, lalu turun ke bawah kartu penutup dan berdampingan dengan "JANJI "BESOK" ~~412~~". |
| 5 | 0:08–2:24 | **Label penghitung tidak terbaca di HP.** Tulisan "JANJI "BESOK"" cuma ±21 px (setara ±7 pt di layar HP), padahal ini alat cerita utama. | Sedang | ✅ HUD diganti versi lebih besar dengan gaya identik (label 30 px, angka 76 px). Nilai per frame dibaca dari video asli via OCR (324 nilai unik). |
| 6 | 0:38,3–0:42,5 | **Gelembung pikiran menutup separuh angka kalender** ("367" kepotong jadi "3б7"). | Sedang | ✅ Kalender disembunyikan halus (fade) selama gelembung tampil, lalu muncul lagi. |
| 7 | 2:33,8–2:36,4 | **Caption menyatu dengan palang penjara.** Palang hitam lewat tepat di belakang kotak caption yang warnanya hampir sama. | Sedang | ✅ Kotak caption di adegan itu diberi garis tepi terang tipis. |
| 8 | 2:24,3 (4 frame) | **Teks "TIDAK ADA JANJI BESOK" muncul di luar kartunya** (teks "hantu" di atas catatan kuning). | Minor | ✅ Bagian di luar kartu dihapus, jadi teksnya terlihat muncul dari tepi kartu. |
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
