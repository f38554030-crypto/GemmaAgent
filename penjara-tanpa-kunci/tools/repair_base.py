"""Pixel-level repairs on the original render, done frame by frame in YUV420 (untouched pixels stay bit-exact).

  1. cube shadow wedge spilling onto the background (city shots)            -> un-shadow (YUV, see wedge_yuv.py)
  2. calendar half-covered by the thought bubble (38.3s-42.5s)               -> calendar hidden while bubble is shown
  3. 'TIDAK ADA JANJI BESOK' rendered outside its card (144.2-144.4s)        -> text outside the card removed

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
GHOST = (4326, 4329)   # 4330-31: the line is already attached to the growing card (left as rendered)


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



def fit_circle(xs, ys):
    A = np.c_[2 * xs, 2 * ys, np.ones_like(xs)]
    b = xs ** 2 + ys ** 2
    c = np.linalg.lstsq(A, b, rcond=None)[0]
    cx, cy = c[0], c[1]
    return cx, cy, float(np.sqrt(max(c[2] + cx * cx + cy * cy, 1e-6)))


def robust_circle(xs, ys):
    keep = np.ones(len(xs), bool)
    for _ in range(4):
        if keep.sum() < 8:
            return None
        cx, cy, r = fit_circle(xs[keep], ys[keep])
        res = np.abs(np.hypot(xs - cx, ys - cy) - r)
        keep = res < max(0.9, 2.5 * np.median(res[keep]))
    return (cx, cy, r, float(np.median(res[keep])), int(keep.sum()))


def bubble_alpha(rgb, lum):
    """Coverage of the thought bubble in the ROI, with its top edge rebuilt from circle fits
    (the cloud outline is made of circular bumps). Returns (alpha, core_mask)."""
    br = rgb[..., 2] - rgb[..., 0]
    # bubble-likeness that works against wall, cream page, navy digits and red header
    s = np.minimum(np.clip((lum - 43.0) / 194.0, 0, 1), np.clip((br + 16.0) / 31.0, 0, 1))
    raw = cv2.morphologyEx((s > 0.5).astype(np.uint8), cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    nb, lb, sb, _ = cv2.connectedComponentsWithStats(raw, 8)
    core = np.zeros(lum.shape, bool)
    for k in range(1, nb):
        if sb[k, cv2.CC_STAT_AREA] >= 400 and s[lb == k].mean() > 0.8:
            core |= lb == k
    Hh, Ww = core.shape
    pts = []
    for x in range(Ww):
        col = np.nonzero(core[:, x])[0]
        if len(col) == 0 or col[0] < 3:
            continue
        y0 = col[0]
        base = y0 - 3
        prof = s[base:y0 + 2, x]
        for j in range(len(prof) - 1):
            if prof[j] < 0.5 <= prof[j + 1]:
                pts.append((x + 0.5, base + j + 0.5 + (0.5 - prof[j]) / (prof[j + 1] - prof[j] + 1e-6)))
                break
    fallback = cv2.GaussianBlur((cv2.GaussianBlur(core.astype(np.float32), (0, 0), 2.0) > 0.5).astype(np.float32), (0, 0), 0.7)
    if len(pts) < 30:
        return fallback, core
    P = np.array(pts)
    xs, ys = P[:, 0], P[:, 1]
    # cusps between cloud bumps = local maxima of y (deepest points of the V notches)
    ysm = np.array([np.median(ys[max(0, i - 2):i + 3]) for i in range(len(ys))])
    cusps = []
    for i in range(len(ys)):
        lo, hi = max(0, i - 14), min(len(ys), i + 15)
        if ysm[i] >= ysm[lo:hi].max() and ysm[i] - ysm[lo:hi].min() > 4 and 0 < i < len(ys) - 1:
            if not cusps or xs[i] - cusps[-1] > 10:
                cusps.append(xs[i])
    edges = [-1e9] + cusps + [1e9]
    circles = []
    for a, b in zip(edges, edges[1:]):
        sel = (xs > a + 3) & (xs < b - 3)
        if sel.sum() < 15:
            continue
        c = robust_circle(xs[sel], ys[sel])
        if c and c[3] < 0.8 and 25 < c[2] < 600:
            circles.append(c)
    if not circles:
        return fallback, core
    yy, xx = np.mgrid[0:Hh, 0:Ww].astype(np.float32)
    yy += 0.5; xx += 0.5
    ag = np.zeros(core.shape, np.float32)
    for cx, cy, r, _, _ in circles:
        ag = np.maximum(ag, np.clip(0.5 - (np.hypot(xx - cx, yy - cy) - r), 0, 1))
    # use the analytic edge only in a band around the measured top boundary
    band = np.zeros(core.shape, np.uint8)
    for x, y in pts:
        band[max(0, int(y) - 7):int(y) + 8, int(x)] = 1
    band = cv2.dilate(band, np.ones((1, 5), np.uint8)).astype(bool)
    alpha = np.where(band, ag, core.astype(np.float32))
    return alpha.astype(np.float32), core


def hide_calendar(Y, U, V, n):
    a = hide_alpha(n)
    hb = cal.get(str(n))
    if a <= 0 or not hb:
        return None
    x0, x1, y0, y1 = hb
    w = x1 - x0 + 1
    bx0, by0, bx1, by1 = even_box(x0 - 110, y0 - 8, x1 + 111, int(y1 + 0.92 * w) + 30)
    rgb = roi_get_rgb(Y, U, V, bx0, by0, bx1, by1).astype(np.float32)
    lum = rgb.mean(-1)
    # calendar footprint (rect incl. its anti-aliased edge), soft outer edge
    m = np.zeros(lum.shape, np.float32)
    cx0, cy0 = x0 - bx0 - 5, y0 - by0 - 4
    cx1, cy1 = x1 - bx0 + 6, int(y1 + 0.92 * w) - by0 + 4
    cv2.rectangle(m, (cx0, cy0), (cx1, cy1), 1.0, -1)
    m = cv2.GaussianBlur(m, (0, 0), 0.8)
    bub_a, bub_core = bubble_alpha(rgb, lum)
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


GHOST_REF = 4325   # the yellow note is static from this frame on, and the ghost text has not appeared yet
GHOST_ROI = (280, 360, 1000, 820)
GHOST_STRADDLE = 4330


def clean_ghost(Y, U, V, n, ref_rgb):
    """Remove the part of 'TIDAK ADA JANJI BESOK' drawn on the yellow note below the card by restoring the
    note from the reference frame (static note). Pixels inside the card are kept, so the text appears to
    slide out from behind the card's bottom edge."""
    if ref_rgb is None:
        return None
    bx0, by0, bx1, by1 = GHOST_ROI
    cur = roi_get_rgb(Y, U, V, bx0, by0, bx1, by1).astype(np.int16)
    ref = ref_rgb.astype(np.int16)
    lum = cur.mean(-1)
    nlab, lab, st, _ = cv2.connectedComponentsWithStats((lum < 45).astype(np.uint8), 8)
    if nlab < 2:
        return None
    k = 1 + int(np.argmax(st[1:, cv2.CC_STAT_AREA]))
    card = (lab == k).astype(np.uint8)
    cx, cy, cw, ch = st[k, 0], st[k, 1], st[k, 2], st[k, 3]
    mid = range(cx + cw // 5, cx + cw - cw // 5)
    yb = int(np.median([np.nonzero(card[:, x])[0].max() for x in mid]))
    # protect the card incl. its 2px border and anti-aliased rim (all darker than the light text),
    # so only the text that overhangs the card is removed and the border stays continuous
    near = cv2.dilate(card, np.ones((9, 9), np.uint8)).astype(bool)
    protect = card.astype(bool) | (near & (lum < 200))
    allowed = np.zeros(card.shape, bool)
    allowed[yb + 2:, max(0, cx - 4):cx + cw + 5] = True
    allowed &= ~protect
    ref_note = (ref[..., 0] > 150) & (ref[..., 1] > 110) & (ref[..., 2] < 170) & (ref[..., 0] - ref[..., 2] > 50)
    # white text over yellow raises the blue channel a lot; the card's soft shadow only darkens -> keep it
    ghost = ((cur[..., 2] - ref[..., 2]) > 6) & allowed & ref_note
    ghost = cv2.dilate(ghost.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool) & allowed & ref_note
    out = cur.copy()
    out[ghost] = ref[ghost]
    inside = 0
    if n == GHOST_STRADDLE:
        # the only frame where the line crosses the card's border: clear the whole line, it appears
        # complete inside the grown card on the next frame
        fill = np.median(cur[card.astype(bool)], axis=0)
        band = np.zeros(card.shape, bool)
        band[max(0, yb - 40):yb - 2, cx + 14:cx + cw - 14] = True
        txt = band & (lum > 50)
        txt = cv2.dilate(txt.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(bool) & band
        out[txt] = fill
        inside = int(txt.sum())
    roi_put_rgb(Y, U, V, bx0, by0, bx1, by1, np.clip(out, 0, 255).astype(np.uint8))
    return {"changed_px": int(ghost.sum()), "inside_px": inside, "card_bottom": int(by0 + yb)}


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
import wedge_fix as wf

TAIL = (2505, 2538)   # end of the first city shot: camera pans right, cube leaves frame, shadow stays visible
geo = {}
tail_state = {}
for n, fr in frames(src):
    if n > WEDGE[-1][1]:
        break
    if not in_ranges(n, WEDGE):
        continue
    Y, U, V = planes(fr)
    if TAIL[0] <= n <= TAIL[1]:
        # zoom has settled (side width const); only horizontal pan. Track the cube's right edge while
        # it is visible, then the shadow's own right edge, predicted from the pan velocity.
        if not tail_state:
            ref = geo[TAIL[0] - 1]
            tail_state = dict(w=ref["bbr_x"] - ref["fbr_x"], fbr_y=ref["fbr_y"], m=ref["m"], y_top=ref["y_top"],
                              d=ref["gr_x"] - (ref["fbr_x"] + wf.GR_RATIO * (ref["bbr_x"] - ref["fbr_x"])),
                              xs=[geo[TAIL[0] - 2]["fbr_x"], ref["fbr_x"]])
        T = tail_state
        rgb = wy.yuv_to_rgb(Y, U, V)
        side = (np.abs(rgb.astype(np.int16) - wf.SIDE).max(-1) <= 6).astype(np.uint8)
        nl, lab, st, _ = cv2.connectedComponentsWithStats(side, 8)
        fbr_x = None
        for k in 1 + np.argsort(-st[1:, cv2.CC_STAT_AREA])[:4]:
            if st[k, cv2.CC_STAT_LEFT] <= 1 and st[k, cv2.CC_STAT_HEIGHT] > 150 and st[k, cv2.CC_STAT_AREA] > 3000:
                fbr_x = st[k, cv2.CC_STAT_LEFT] + st[k, cv2.CC_STAT_WIDTH] - 1 + 0.5 - T["w"]
                break
        pred = T["xs"][-1] + (T["xs"][-1] - T["xs"][-2])
        if fbr_x is None or abs(fbr_x - pred) > 60:
            fbr = (pred, T["fbr_y"]); bbr = (pred + T["w"], T["fbr_y"] + T["m"] * T["w"])
            y_top, gr_x, tr_x, nt, nr = wf.refine(Y.astype(np.float32) - 16.0, fbr, bbr)
            fbr_x = gr_x - wf.GR_RATIO * T["w"] - T["d"] if nr >= 6 else pred
            if nt >= 5:
                T["y_top"] = y_top
        T["xs"].append(fbr_x)
        g = dict(fbr_x=fbr_x, fbr_y=T["fbr_y"], bbr_x=fbr_x + T["w"], bbr_y=T["fbr_y"] + T["m"] * T["w"], m=T["m"],
                 y_top=T["y_top"], gr_x=fbr_x + wf.GR_RATIO * T["w"] + T["d"])
        g["tr_x"] = g["gr_x"] + (g["fbr_y"] - g["y_top"]) * wf.RIGHT_SLOPE
        geo[n] = g if g["tr_x"] > -5 else None
        continue
    geo[n] = wy.measure(Y, U, V)

# reject single-frame mis-measurements on the RAW data (e.g. a walking figure biasing the top-edge step at
# 188.5s): compare each frame with a robust quadratic through its neighbours (itself excluded)
rejected = []
meas = sorted(k for k, v in geo.items() if v)
# local camera speed (px/frame of the shadow's right edge). During fast pans the raw measurements are
# accurate (clean background, strong edges) and polynomial models lag behind -> keep raw values there.
speed = {}
for n in meas:
    # net displacement (robust to single-frame jitter): median of the 4 frames after minus the 4 before
    after = [geo[k]["gr_x"] for k in meas if 1 <= k - n <= 4]
    before = [geo[k]["gr_x"] for k in meas if 1 <= n - k <= 4]
    speed[n] = abs(float(np.median(after)) - float(np.median(before))) / 5.0 if after and before else 0.0
FAST = 4.0
for n in meas:
    if speed[n] > FAST:
        continue
    nb = [k for k in meas if 0 < abs(k - n) <= 8]
    if len(nb) < 6:
        continue
    for key in ("y_top", "gr_x"):
        xs = np.array(nb, float); ys = np.array([geo[k][key] for k in nb])
        keep = np.ones(len(xs), bool)
        for _ in range(3):
            c = np.polyfit(xs[keep], ys[keep], 2)
            res = np.abs(np.polyval(c, xs) - ys)
            keep = res <= max(1.0, 3 * np.median(res[keep]))
        if abs(np.polyval(c, n) - geo[n][key]) > 2.0:
            rejected.append(n)
            break
for n in rejected:
    geo[n] = None
print("rejected outliers", rejected, flush=True)

# fill short detection gaps (occlusion by figure / flying notes / end card) by interpolation
det = sorted(k for k, v in geo.items() if v)
filled = {}
for a, b in zip(det, det[1:]):
    if 1 < b - a <= 10:
        for k in range(a + 1, b):
            t = (k - a) / (b - a)
            filled[k] = {key: geo[a][key] * (1 - t) + geo[b][key] * t for key in KEYS}
for k, v in filled.items():
    geo[k] = v
# extend through fades where detection fails (scene fade-in at 77.4s, dip before the cut at 100.3s,
# final fade to black): linear extrapolation from the 10 nearest measured frames
EXTEND = [(2330, list(range(2323, 2330)), +1), (3007, [3008], -1), (5712, list(range(5713, 5724)), -1)]
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


# robust temporal smoothing of the geometry inside each continuous run (camera moves smoothly; single-frame
# mis-measurements of an edge would otherwise brighten unshadowed pixels, e.g. 188.4s)
def runs_of(keys):
    out, cur = [], []
    for k in keys:
        if cur and k != cur[-1] + 1:
            out.append(cur); cur = []
        cur.append(k)
    if cur:
        out.append(cur)
    return out


HALF = 6
for run in runs_of(sorted(k for k, v in geo.items() if v)):
    if len(run) < 5:
        continue
    for key in ("fbr_x", "fbr_y", "bbr_x", "bbr_y", "y_top", "gr_x"):
        vals = np.array([geo[k][key] for k in run], float)
        sm = vals.copy()
        for i in range(len(run)):
            lo, hi = max(0, i - HALF), min(len(run), i + HALF + 1)
            xs = np.arange(lo, hi, dtype=float); ys = vals[lo:hi]
            deg = 2 if len(xs) >= 7 else 1
            keep = np.ones(len(xs), bool)
            for _ in range(3):
                c = np.polyfit(xs[keep], ys[keep], deg)
                res = np.abs(np.polyval(c, xs) - ys)
                keep = res <= max(1.2, 3 * np.median(res[keep]))
                if keep.sum() < deg + 2:
                    keep = np.ones(len(xs), bool)
                    break
            sm[i] = np.polyval(c, i)
        for i, k in enumerate(run):
            if speed.get(k, 0.0) <= FAST:      # fast camera moves keep their (accurate) raw values
                geo[k][key] = float(sm[i])
    mmed = float(np.median([geo[k]["m"] for k in run]))
    for k in run:
        geo[k]["m"] = mmed
        geo[k]["tr_x"] = geo[k]["gr_x"] + (geo[k]["fbr_y"] - geo[k]["y_top"]) * wf.RIGHT_SLOPE
print("pass1: measured", sum(1 for v in geo.values() if v), "filled", len(filled), flush=True)

# ---- pass 2: apply all repairs and encode
enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "yuv420p", "-s", f"{W}x{H}",
                        "-r", "30", "-i", "-", "-i", src, "-map", "0:v", "-map", "1:a", "-c:v", "libx264",
                        "-preset", "fast", "-crf", "4", "-pix_fmt", "yuv420p", "-color_range", "tv",
                        "-color_primaries", "bt709", "-color_trc", "iec61966-2-1", "-c:a", "copy",
                        "-movflags", "+faststart", out_p], stdin=subprocess.PIPE, bufsize=FS * 4)
log = {}
ghost_ref = None
for n, fr in frames(src):
    Y, U, V = planes(fr)
    if n == GHOST_REF:
        ghost_ref = roi_get_rgb(Y, U, V, *GHOST_ROI).copy()
    entry = {}
    g = geo.get(n)
    if g:
        Yo, Uo, Vo = wy.apply(Y, U, V, g)
        Y[:], U[:], V[:] = Yo, Uo, Vo
        entry["wedge"] = {k: round(g[k], 2) for k in KEYS} | {"interp": n in filled}
    if BUBBLE[0] <= n <= BUBBLE[1]:
        entry["calendar_hide"] = hide_calendar(Y, U, V, n)
    if GHOST[0] <= n <= GHOST[1]:
        entry["ghost"] = clean_ghost(Y, U, V, n, ghost_ref)
    if entry:
        log[n] = entry
    enc.stdin.write(fr.tobytes())
    if n % 1000 == 0:
        print("frame", n, flush=True)
enc.stdin.close(); enc.wait()
json.dump(log, open(log_p, "w"))
print("done")
