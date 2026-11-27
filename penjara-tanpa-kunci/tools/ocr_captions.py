"""OCR the burned-in caption band over time.
Usage: python ocr_captions.py <video> <out.json> [step_seconds]
For each sampled frame: crop caption band, make two binarized versions:
  all  = any text pixel (dim + bright words)  -> full caption page
  lit  = only bright pixels (already-spoken words in karaoke) -> highlighted part
"""
import sys, json, subprocess, os, tempfile
from concurrent.futures import ThreadPoolExecutor
import numpy as np
from PIL import Image

video, out = sys.argv[1], sys.argv[2]
step = float(sys.argv[3]) if len(sys.argv) > 3 else 0.2
Y0, Y1 = 1330, 1660  # caption band (full-res coordinates)

dur = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", video]).decode())
tmp = tempfile.mkdtemp(dir=os.path.dirname(os.path.abspath(out)))
# extract all needed crops in one ffmpeg pass
fps = 1.0 / step
subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", video, "-vf",
                f"fps={fps},crop=1080:{Y1-Y0}:0:{Y0}", f"{tmp}/c_%05d.png"], check=True)
files = sorted(os.listdir(tmp))

def tess(img):
    p = tempfile.mktemp(suffix=".png", dir=tmp)
    img.save(p)
    r = subprocess.run(["tesseract", p, "-", "-l", "ind", "--psm", "6"], capture_output=True, text=True, env={**os.environ, "OMP_THREAD_LIMIT": "1"})
    os.remove(p)
    return " ".join(r.stdout.split())

def proc(i_f):
    i, f = i_f
    t = round(i * step, 2)
    a = np.asarray(Image.open(os.path.join(tmp, f)).convert("RGB")).astype(np.float32)
    lum = 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]
    sat = a.max(-1) - a.min(-1)
    # text is either bright (white/yellow) or dim grey on a near-black pill
    all_mask = lum > 85
    lit_mask = lum > 150
    def to_img(m):
        im = np.where(m, 0, 255).astype(np.uint8)
        return Image.fromarray(im).resize((im.shape[1], im.shape[0]))
    return {"t": t, "all": tess(to_img(all_mask)), "lit": tess(to_img(lit_mask))}

with ThreadPoolExecutor(max_workers=4) as ex:
    res = list(ex.map(proc, list(enumerate(files))))
json.dump(res, open(out, "w"), ensure_ascii=False, indent=0)
print("frames:", len(res))
