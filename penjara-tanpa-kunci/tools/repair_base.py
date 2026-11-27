"""Pixel-level repairs on the original render, done frame by frame in YUV420 (untouched pixels stay bit-exact).

  1. cube shadow wedge spilling onto the background (city shots)            -> un-shadow (YUV, see wedge_yuv.py)
  2. calendar half-covered by the thought bubble (38.3s-42.5s)               -> calendar hidden while bubble is shown
  3. 'TIDAK ADA JANJI BESOK' rendered outside its card for 4 frames (144.3s) -> text outside the card removed

Usage: repair_base.py <original.mp4> <calendar.json> <out.mp4> <log.json>
"""
import sys, json, subprocess
import numpy as np, cv2
import wedge_yuv as wy

src, cal_p, out_p, log_p = sys.argv[1:5]
W, H = 1080, 1920
FS = W * H * 3 // 2
cal = json.load(open(cal_p))

_oc = wy.chroma_cov
wy.chroma_cov = lambda c, mode="box": cv2.GaussianBlur(_oc(c, "box"), (0, 0), 0.75)

WEDGE = [(2323, 3075), (4915, 5262), (5630, 5724)]
BUBBLE = (1148, 1276)        # calendar hidden (with fades) while the thought bubble is up
GHOST = (4328, 4331)


def in_ranges(n, rs):
    return any(a <= n <= b for a, b in rs)


def smooth(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def hide_alpha(n):
    a0, a1 = BUBBLE
    return min(smooth((n - a0) / 7.0), smooth((a1 - n) / 7.0))


def roi_get_rgb(Y, U, V, x0, y0, x1, y1):
    y = Y[y0:y1, x0:x1]; u = U[y0 // 2:y1 // 2, x0 // 2:x1 // 2]; v = V[y0 // 2:y1 // 2, x0 // 2:x1 // 2]
    i420 = np.concatenate([y.reshape(-1), u.reshape(-1), v.reshape(-1)]).reshape((y1 - y0) * 3 // 2, x1 - x0)
    return cv2.cvtColor(i420, cv2.COLOR_YUV2RGB_I420)


def roi_put_rgb(Y, U, V, x0, y0, x1, y1, rgb):
    i420 = cv2.cvtColor(rgb, cv2.COLOR_RGB2YUV_I420)
    h, w = y1 - y0, x1 - x0
    Y[y0:y1, x0:x1] = i420[:h]
    rest = i420[h:].reshape(-1)
    U[y0 // 2:y1 // 2, x0 // 2:x1 // 2] = rest[:h * w // 4].reshape(h // 2, w // 2)
    V[y0 // 2:y1 // 2, x0 // 2:x1 // 2] = rest[h * w // 4:].reshape(h // 2, w // 2)


def even_box(x0, y0, x1, y1):
    x0 -= x0 % 2; y0 -= y0 % 2; x1 += x1 % 2; y1 += y1 % 2
    return max(0, x0), max(0, y0), min(W, x1), min(H, y1)


def hide_calendar(Y, U, V, n):
    a = hide_alpha(n)
    hb = cal.get(str(n))
    if a <= 0 or not hb:
        return None
    x0, x1, y0, y1 = hb
    w = x1 - x0 + 1
    bx0, by0, bx1, by1 = even_box(x0 - 40, y0 - 8, x1 + 41, int(y1 + 0.92 * w) + 8)
    rgb = roi_get_rgb(Y, U, V, bx0, by0, bx1, by1).astype(np.float32)
    lum = rgb.mean(-1)
    # calendar footprint (rect incl. its anti-aliased edge), soft outer edge
    m = np.zeros(lum.shape, np.float32)
    cx0, cy0 = x0 - bx0 - 5, y0 - by0 - 4
    cx1, cy1 = x1 - bx0 + 6, int(y1 + 0.92 * w) - by0 + 4
    cv2.rectangle(m, (cx0, cy0), (cx1, cy1), 1.0, -1)
    m = cv2.GaussianBlur(m, (0, 0), 0.8)
    # bubble = cool white (B-R ~ +15); calendar page = warm cream (B-R ~ -16); digits are dark
    bub_raw = (((rgb[..., 2] - rgb[..., 0]) > 4) & (lum > 200)).astype(np.uint8)
    bub_raw = cv2.morphologyEx(bub_raw, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    nb, lb, sb, _ = cv2.connectedComponentsWithStats(bub_raw, 8)
    bub_core = np.zeros(lum.shape, bool)
    for k in range(1, nb):
        if sb[k, cv2.CC_STAT_AREA] >= 3000:      # the bubble itself; drops compression specks near the digits
            bub_core |= lb == k
    # smooth the classified bubble silhouette (cloud arcs have large radii), then anti-alias it
    core_s = cv2.GaussianBlur(bub_core.astype(np.float32), (0, 0), 2.0) > 0.5
    bub_a = cv2.GaussianBlur(core_s.astype(np.float32), (0, 0), 0.7)
    bub_col = np.median(rgb[bub_core], axis=0) if bub_core.sum() > 50 else np.array([230, 236, 245], np.float32)
    # wall colour per row from both sides of the calendar (exclude notes / bubble)
    fill = np.zeros_like(rgb)
    left = rgb[:, max(0, cx0 - 30):max(1, cx0 - 4)]
    right = rgb[:, cx1 + 4:cx1 + 30]
    ring = np.concatenate([left, right], axis=1)
    rl = ring.mean(-1)
    wall_like = (rl < 70) & (ring[..., 2] > ring[..., 0])
    glob_wall = np.median(ring[wall_like], axis=0) if wall_like.any() else np.array([28, 36, 60], np.float32)
    for r in range(rgb.shape[0]):
        sel = wall_like[r]
        fill[r] = np.median(ring[r][sel], axis=0) if sel.sum() >= 6 else glob_wall
    fill = cv2.GaussianBlur(fill, (1, 9), 0)
    # inside the footprint: bubble where the bubble is, wall elsewhere (re-anti-aliased bubble edge)
    new = bub_a[..., None] * bub_col + (1 - bub_a[..., None]) * fill
    mm = (m * a)[..., None]
    out = rgb * (1 - mm) + new * mm
    roi_put_rgb(Y, U, V, bx0, by0, bx1, by1, np.clip(out + 0.5, 0, 255).astype(np.uint8))
    return {"alpha": round(a, 3), "box": [bx0, by0, bx1, by1]}


def clean_ghost(Y, U, V, n):
    bx0, by0, bx1, by1 = 300, 380, 960, 760
    rgb = roi_get_rgb(Y, U, V, bx0, by0, bx1, by1).astype(np.float32)
    lum = rgb.mean(-1)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    yellow = (r > 180) & (g > 140) & (b < 140) & (r - b > 70)
    dark = lum < 45
    # box = big dark component; its bottom edge per column
    nlab, lab, st, _ = cv2.connectedComponentsWithStats(dark.astype(np.uint8), 8)
    if nlab < 2:
        return None
    k = 1 + int(np.argmax(st[1:, cv2.CC_STAT_AREA]))
    box = lab == k
    cols = np.nonzero(box.any(0))[0]
    out = rgb.copy()
    changed = 0
    for x in cols:
        yb = np.nonzero(box[:, x])[0].max()
        y_a, y_b = yb + 2, min(rgb.shape[0], yb + 60)
        seg = out[y_a:y_b, x]
        yel = yellow[y_a:y_b, x]
        if yel.sum() < 5:
            continue
        ref = np.median(seg[yel], axis=0)
        bad = ~yel
        # also the 1-2 px anti-aliased fringe right under the box edge stays (it belongs to the box)
        if bad.any():
            seg[bad] = ref
            changed += int(bad.sum())
    roi_put_rgb(Y, U, V, bx0, by0, bx1, by1, np.clip(out + 0.5, 0, 255).astype(np.uint8))
    return {"changed_px": changed}


KEYS = ["fbr_x", "fbr_y", "bbr_x", "bbr_y", "m", "y_top", "gr_x", "tr_x"]


def frames(path):
    d = subprocess.Popen(["ffmpeg", "-v", "error", "-i", path, "-f", "rawvideo", "-pix_fmt", "yuv420p", "-"],
                         stdout=subprocess.PIPE, bufsize=FS * 4)
    k = 0
    while True:
        b = d.stdout.read(FS)
        if not b or len(b) < FS:
            break
        yield k, np.frombuffer(b, np.uint8).copy()
        k += 1
    d.wait()


def planes(fr):
    return (fr[:W * H].reshape(H, W), fr[W * H:W * H * 5 // 4].reshape(H // 2, W // 2), fr[W * H * 5 // 4:].reshape(H // 2, W // 2))


# ---- pass 1: measure cube/shadow geometry on every frame of the city shots
geo = {}
for n, fr in frames(src):
    if in_ranges(n, WEDGE):
        Y, U, V = planes(fr)
        geo[n] = wy.measure(Y, U, V)
    elif n > WEDGE[-1][1]:
        break
# fill short detection gaps (occlusion by figure / flying notes / end card) by interpolation
det = sorted(k for k, v in geo.items() if v)
filled = {}
for a, b in zip(det, det[1:]):
    if 1 < b - a <= 8:
        for k in range(a + 1, b):
            t = (k - a) / (b - a)
            filled[k] = {key: geo[a][key] * (1 - t) + geo[b][key] * t for key in KEYS}
for k, v in filled.items():
    geo[k] = v
# de-spike: a frame whose edges disagree with both neighbours (static camera) -> average of neighbours
for k in sorted(geo):
    a, b = geo.get(k - 1), geo.get(k + 1)
    v = geo[k]
    if v and a and b:
        for key in ("y_top", "gr_x"):
            if abs(a[key] - b[key]) < 3 and abs(v[key] - (a[key] + b[key]) / 2) > 4:
                v[key] = (a[key] + b[key]) / 2
                v["tr_x"] = v["gr_x"] + (v["fbr_y"] - v["y_top"]) * 1.27
# extend through fades where detection fails (scene fade-in at 77.4s, final fade to black):
# linear extrapolation from the 10 nearest measured frames (camera drifts < 1 px/frame there)
EXTEND = [(2330, list(range(2323, 2330)), +1), (5712, list(range(5713, 5724)), -1)]
extrap = []
for anchor, targets, direction in EXTEND:
    ref = [anchor + direction * i for i in range(10)]
    if not all(geo.get(r) for r in ref):
        continue
    for t in targets:
        if geo.get(t):
            continue
        g = {}
        for key in KEYS:
            xs = np.array(ref, float); ys = np.array([geo[r][key] for r in ref])
            a, b = np.polyfit(xs, ys, 1)
            g[key] = float(a * t + b)
        geo[t] = g
        filled[t] = g
        extrap.append(t)
print("extrapolated", extrap, flush=True)
print("pass1: measured", sum(1 for v in geo.values() if v), "filled", len(filled), flush=True)

# ---- pass 2: apply all repairs and encode
enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "yuv420p", "-s", f"{W}x{H}",
                        "-r", "30", "-i", "-", "-i", src, "-map", "0:v", "-map", "1:a", "-c:v", "libx264",
                        "-preset", "fast", "-crf", "4", "-pix_fmt", "yuv420p", "-color_range", "tv",
                        "-color_primaries", "bt709", "-color_trc", "iec61966-2-1", "-c:a", "copy",
                        "-movflags", "+faststart", out_p], stdin=subprocess.PIPE, bufsize=FS * 4)
log = {}
for n, fr in frames(src):
    Y, U, V = planes(fr)
    entry = {}
    g = geo.get(n)
    if g:
        Yo, Uo, Vo = wy.apply(Y, U, V, g)
        Y[:], U[:], V[:] = Yo, Uo, Vo
        entry["wedge"] = {k: round(g[k], 2) for k in KEYS} | {"interp": n in filled}
    if BUBBLE[0] <= n <= BUBBLE[1]:
        entry["calendar_hide"] = hide_calendar(Y, U, V, n)
    if GHOST[0] <= n <= GHOST[1]:
        entry["ghost"] = clean_ghost(Y, U, V, n)
    if entry:
        log[n] = entry
    enc.stdin.write(fr.tobytes())
    if n % 1000 == 0:
        print("frame", n, flush=True)
enc.stdin.close(); enc.wait()
json.dump(log, open(log_p, "w"))
print("done")
