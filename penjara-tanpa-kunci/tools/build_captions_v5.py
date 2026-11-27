"""Build caption pages with per-word timing for v5 (text-only captions, karaoke highlight).

Usage: build_captions_v5.py <transcript_large.json> <out captions.json>
Word times come from Whisper (word timestamps) aligned at character level to the caption text below, with the
first word of a phrase clamped to the speech onset (VAD) because Whisper stretches leading words into silence.
"""
import sys, json, re, difflib

PAGES = [
    "Setiap malam ia membuat *janji*", "yang sama kepada dirinya sendiri.",
    "“*Besok*, aku akan mulai berubah.”",
    "Namun, ketika *esok* tiba,", "semuanya kembali *berulang*.",
    "*Alarm* dimatikan,", "ponsel kembali *digenggam*,", "berjam-jam berlalu *tanpa terasa*.",
    "Malam menjadi *pagi*,", "hari berganti *minggu*,", "minggu menjadi *bulan*,", "waktu terus *berjalan*,",
    "sementara kehidupannya seolah", "tidak *bergerak* ke mana-mana.",
    "Sebenarnya, ia *sadar*.", "Tidurnya semakin *berantakan*,", "tubuhnya mulai *kelelahan*.",
    "Berbagai *rencana* yang dahulu", "begitu berarti,", "kini hanya tersimpan dalam *pikirannya*.",
    "Ia *ingin berubah*,", "namun setiap kali *kesempatan* itu datang,", "selalu muncul *alasan*.",
    "“Hari ini terlalu *melelahkan*.”", "“Masih ada banyak *waktu*.”", "“Aku akan mulai ketika semuanya sudah *siap*.”",
    "Alasan-alasan itu terdengar *masuk akal*,", "bahkan terkadang memang *benar*.",
    "Tetapi perlahan,", "alasan yang seharusnya menjelaskan *kesulitan*", "justru menjadi *pembenaran*",
    "untuk terus *menghindarinya*.",
    "Dan tanpa disadari,", "sesuatu yang awalnya hanya *kebiasaan*", "mulai berubah menjadi *batas* kehidupannya.",
    "Sementara itu,", "dunia di luar terus *bergerak*.",
    "Manusia *belajar*, *bekerja*,", "*bertemu*, mengalami *kegagalan*,", "menemukan *hal baru*,",
    "dan *mencoba kembali*.",
    "Dunia itu tidak selalu *indah*,", "tidak selalu *mudah*,", "bahkan penuh *ketidakpastian*.",
    "Namun, ada *pengalaman*", "yang tidak mungkin diperoleh", "hanya dengan terus *membayangkannya*.",
    "Suatu malam,", "ia *berhenti* menatap layar ponselnya.",
    "Pandangannya beralih ke sekeliling ruangan:", "*ranjang*,", "*dinding* yang sunyi,", "*ventilasi* kecil,",
    "dan sebuah *pintu*.",
    "Untuk pertama kalinya,", "ia *mempertanyakan* sesuatu", "yang selama ini selalu *dihindarinya*.",
    "“Apakah aku benar-benar", "*tidak mampu* mengubah hidupku?", "Atau selama ini aku terus",
    "*mencari alasan* untuk tidak mencobanya?”",
    "Ia *terdiam*.", "Tidak ada jawaban yang langsung", "mengubah *segalanya*.",
    "Rasa *takutnya* masih ada,", "*keraguannya* belum menghilang.",
    "Namun, *kali ini*", "ia *tidak* membuat janji untuk esok hari.",
    "Ia meletakkan *ponselnya*,", "*bangkit* dari ranjang,", "dan perlahan berjalan menuju *pintu*.",
    "Ketika tangannya *memutar*", "gagang pintu itu,", "ia menemukan sesuatu", "yang tak pernah benar-benar ia *sadari*.",
    "Pintu tersebut", "*tidak terkunci*.",
    "Di hadapannya terbentang dunia yang *luas*,", "dengan segala *kemungkinan*", "dan *ketidakpastiannya*.",
    "Ia belum tahu apa yang akan terjadi *selanjutnya*,", "tetapi kini ia memahami *satu hal*:",
    "*kesadaran* tidak akan mengubah kehidupan", "selama tidak diikuti *keberanian*", "untuk mengambil *tindakan*.",
    "Dan *perubahan* itu", "dimulai dari *langkah pertamanya*.",
]

# speech onsets (Silero VAD) - used to clamp leading words that Whisper stretches into silence
VAD = [1.70, 3.00, 6.40, 7.20, 10.40, 13.20, 15.20, 16.70, 18.60, 21.40, 23.20, 24.90, 26.60, 32.20, 34.50, 36.40,
       38.20, 43.50, 44.80, 48.30, 50.10, 52.70, 54.50, 57.80, 60.60, 62.60, 66.80, 71.00, 79.40, 83.20, 91.00,
       92.90, 94.30, 97.30, 104.00, 107.70, 110.50, 111.50, 113.00, 114.30, 115.90, 116.60, 122.80, 126.70, 131.60,
       133.40, 136.60, 138.50, 140.70, 142.00, 146.70, 153.80, 162.90, 164.40, 167.80, 171.20, 174.10, 177.90,
       180.40, 184.40]


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def main(tr_p, out_p):
    tr = json.load(open(tr_p))
    ww = [w for s in tr["segments"] for w in s["words"] if w["s"] < 188.5]
    # character streams
    wchars, wtime = [], []
    for w in ww:
        n = norm(w["w"])
        for i, ch in enumerate(n):
            wchars.append(ch)
            wtime.append(w["s"] + (w["e"] - w["s"]) * i / max(1, len(n)))
    words = []   # (page_idx, display_text, highlight)
    for pi, p in enumerate(PAGES):
        # mark highlighted characters, then split on spaces (punctuation stays attached to its word)
        clean, hl, on = [], [], False
        for ch in p:
            if ch == "*":
                on = not on
                continue
            clean.append(ch); hl.append(on)
        text = "".join(clean)
        pos = 0
        for tok in text.split(" "):
            if tok:
                flags = hl[pos:pos + len(tok)]
                letters = [f for ch, f in zip(tok, flags) if ch.isalnum()]
                words.append([pi, tok, any(letters)])
            pos += len(tok) + 1
    # fix highlight tokens that carried punctuation outside the asterisks (e.g. "*esok* tiba," handled above)
    cchars, cowner = [], []
    for wi, (_, t, _) in enumerate(words):
        for ch in norm(t):
            cchars.append(ch); cowner.append(wi)
    sm = difflib.SequenceMatcher(None, cchars, wchars, autojunk=False)
    ctime = [None] * len(cchars)
    for a, b, size in sm.get_matching_blocks():
        for k in range(size):
            ctime[a + k] = wtime[b + k]
    # word start = time of its first matched char (interpolate gaps)
    wstart = [None] * len(words)
    for ci, wi in enumerate(cowner):
        if wstart[wi] is None and ctime[ci] is not None:
            # back-project to the word start using the char position inside the word
            first = cowner.index(wi)
            wstart[wi] = ctime[ci] - 0.06 * (ci - first)
    for i in range(len(words)):
        if wstart[i] is None:
            prev = next((wstart[j] for j in range(i - 1, -1, -1) if wstart[j] is not None), 0.0)
            nxt = next((wstart[j] for j in range(i + 1, len(words)) if wstart[j] is not None), prev + 0.3)
            wstart[i] = (prev + nxt) / 2
    # clamp words that start in silence before a VAD onset (Whisper stretches leading words)
    for i in range(len(words)):
        later = [v for v in VAD if v > wstart[i] - 0.02]
        if later and later[0] - wstart[i] > 0.25 and (i == 0 or wstart[i] - wstart[i - 1] > 0.5 or words[i][0] != words[i - 1][0]):
            nxt = wstart[i + 1] if i + 1 < len(words) else later[0] + 0.3
            if later[0] < nxt:
                wstart[i] = later[0]
    # monotonic
    for i in range(1, len(words)):
        wstart[i] = max(wstart[i], wstart[i - 1] + 0.05)
    pages = []
    for pi, p in enumerate(PAGES):
        idx = [i for i, w in enumerate(words) if w[0] == pi]
        pages.append({"text": p.replace("*", ""), "words": [{"t": words[i][1], "s": round(wstart[i], 3), "hl": words[i][2]} for i in idx]})
    # page in/out
    for k, pg in enumerate(pages):
        s0 = pg["words"][0]["s"]
        last = pg["words"][-1]["s"]
        pg["in"] = round(s0 - 0.12, 3)
        nxt = pages[k + 1]["words"][0]["s"] - 0.12 if k + 1 < len(pages) else 188.3
        pg["out"] = round(min(nxt, last + 1.6), 3)
    json.dump(pages, open(out_p, "w"), ensure_ascii=False, indent=1)
    for pg in pages:
        print(f'{pg["in"]:7.2f}-{pg["out"]:7.2f}  {pg["text"]}')


if __name__ == "__main__":
    main(*sys.argv[1:3])
