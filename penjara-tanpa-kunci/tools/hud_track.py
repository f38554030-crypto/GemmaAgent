"""Per-frame HUD state of the original video: value (OCR of the digits) and opacity.
Output JSON: {frame: {"v": int|None, "a": float}} for frames where the HUD box is present.
HUD box (full opacity): x 747-1008, y 247-372, fill ~RGB(16,16,16), label gold, digits white.
"""
import sys, json, subprocess, os, tempfile, hashlib, uuid
from concurrent.futures import ThreadPoolExecutor
import numpy as np, cv2

video, out = sys.argv[1], sys.argv[2]
ranges = [(230, 4420), (5570, 5660)]          # frames to scan (7.7s-147.3s, 185.7s-188.7s)
cap = cv2.VideoCapture(video)
tmpd = tempfile.mkdtemp(dir=os.path.dirname(os.path.abspath(out)))
env = {**os.environ, "OMP_THREAD_LIMIT": "1"}
cache = {}

def ocr_digits(binimg):
    key = hashlib.md5(binimg.tobytes()).hexdigest()
    if key in cache:
        return cache[key]
    p = os.path.join(tmpd, key + "_" + uuid.uuid4().hex + ".png")
    big = cv2.resize(255 - binimg, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC)
    big = cv2.copyMakeBorder(big, 20, 20, 20, 20, cv2.BORDER_CONSTANT, value=255)
    cv2.imwrite(p, big)
    r = subprocess.run(["tesseract", p, "-", "--psm", "7", "-c", "tessedit_char_whitelist=0123456789"],
                       capture_output=True, text=True, env=env)
    os.remove(p)
    s = "".join(ch for ch in r.stdout if ch.isdigit())
    cache[key] = int(s) if s else None
    return cache[key]

jobs = []
for a, b in ranges:
    cap.set(cv2.CAP_PROP_POS_FRAMES, a)
    for n in range(a, b):
        ok, f = cap.read()
        if not ok:
            break
        rgb = f[..., ::-1].astype(np.float32)
        box = rgb[247:373, 747:1009]
        lab = rgb[262:296, 770:990]        # label row
        num = rgb[296:362, 800:1000]       # number area
        # opacity from gold label: R channel high, B lower
        gold = lab[..., 0] - lab[..., 2]
        gscore = np.percentile(gold, 99.5)  # ~ (233-122)=111 at full opacity
        wscore = np.percentile(num.mean(-1), 99.5)
        fill = np.median(box[box.mean(-1) < 60].mean(-1)) if (box.mean(-1) < 60).any() else 99
        jobs.append((n, gscore, wscore, fill, (num.mean(-1) > 150).astype(np.uint8) * 255))

def proc(j):
    n, g, w, fill, binimg = j
    a = float(np.clip(g / 108.0, 0, 1))
    v = ocr_digits(binimg) if (a > 0.35 and binimg.sum() > 255 * 80) else None
    return n, {"v": v, "a": round(a, 3), "w": round(float(w), 1), "fill": round(float(fill), 1)}

with ThreadPoolExecutor(max_workers=4) as ex:
    res = dict(ex.map(proc, jobs))
json.dump(res, open(out, "w"))
print("frames", len(res), "unique OCR", len(cache))
