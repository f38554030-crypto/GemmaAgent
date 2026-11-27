# Penjara Tanpa Kunci — perbaikan v4

Folder ini berisi semua yang dipakai untuk menganalisis dan memperbaiki `Penjara_Tanpa_Kunci_v3_final.mp4` menjadi v4.
Daftar masalah dan statusnya ada di [`ANALISIS.md`](ANALISIS.md).

Source code Remotion v3 tidak tersedia, jadi v4 dibuat dengan cara ini:
1. Analisis video v3 (frame, audio, transkrip, OCR).
2. Perbaikan tingkat piksel pada video v3 (Python).
3. Overlay Remotion baru di atas hasil perbaikan itu.

## Isi folder

```
penjara-tanpa-kunci/
├── ANALISIS.md            daftar masalah, tingkat keparahan, status perbaikan
├── tools/                 skrip Python/Bash (analisis + perbaikan piksel)
├── data/                  hasil analisis (transkrip, OCR, data HUD/kalender per frame, log perbaikan)
└── remotion/              proyek Remotion 4.0.547 untuk overlay + render final
    ├── src/Main.tsx       susunan lapisan
    ├── src/components/    Hook, HudFix, CalendarFix, DoorCaptionOutline, Payoff, HudBox
    ├── src/data/          data per frame untuk overlay (dibuat oleh tools/build_overlay_data.py)
    └── public/            font Nunito + base_repaired.mp4 (tidak di-commit, ukurannya besar)
```

## Cara render ulang (di Claude Code cloud / Linux)

Dibutuhkan: `ffmpeg`, Python 3 + `numpy opencv-python-headless pillow`, Node 22.

```bash
# 1) perbaikan piksel: bayangan kubus, kalender di balik gelembung, teks hantu
python tools/repair_base.py v3.mp4 data/calendar.json remotion/public/base_repaired.mp4 repair_log.json
```

```bash
# 2) render overlay + video (tanpa audio)
cd remotion && npm install && npm run render
```

```bash
# 3) pasang audio asli v3 tanpa re-encode
ffmpeg -i remotion/out/video_only.mp4 -i v3.mp4 -map 0:v -map 1:a -c copy -movflags +faststart Penjara_Tanpa_Kunci_v4.mp4
```

Pratinjau di Remotion Studio: `cd remotion && npm run studio`.

## Catatan teknis singkat
- Bayangan kubus = lapisan hitam transparan 12,5%. Penghapusannya dihitung di ruang YUV (Y−16 dan U/V−128 dibagi 0,875). Mask kecerahan dibuat tajam, sedangkan mask warna dibuat setengah resolusi dan diblur σ 0,75 supaya cocok dengan chroma 4:2:0. Tepi bayangan diukur sub-piksel per frame; frame yang tertutup tokoh/kartu diisi dengan interpolasi.
- Kalender: pose halaman yang jatuh (pusat, sudut, ukuran) dipasang per frame dengan memaksimalkan IoU terhadap area krem. Angka yang ditulis = angka terakhir yang terlihat sebelum halaman disobek.
- HUD baru menutup HUD lama 100% (kotak lama opaque). Nilai per frame berasal dari OCR video v3. Sebelum HUD lama terbang ke tengah (frame 4319), HUD baru menyusut tepat ke ukuran HUD lama, jadi serah terimanya tidak terlihat.
- Hook baru menampilkan caption asli lewat "lubang" berbentuk pill (frame 52–76), jadi sambungan ke adegan kamar di frame 77 identik.
