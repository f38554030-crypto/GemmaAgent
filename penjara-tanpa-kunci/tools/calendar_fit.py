"""Fit the falling (torn-off) calendar page per frame and decide which number it must carry.

Original bug: every tear cycle draws a BLANK page on top that falls away, while the number sits on
the page underneath.  A real tear-off calendar tears the page that shows the current day.
Fix data: for every frame with a cover/falling page, output its pose (centre, angle, size) and the
number it should show = last number that was visible before the cycle started.

Usage: calendar_fit.py <video> <calendar.json> <calendar_digits.json> <hud.json> <out.json>
"""
import sys, json
import numpy as np, cv2

video, cal_p, dig_p, hud_p, out_p = sys.argv[1:6]
cal = json.load(open(cal_p)); dig = json.load(open(dig_p)); hud = json.load(open(hud_p))
F0, F1 = 550, 930
PAGE_H = 0.90  # page height / header width


def hud_val(n):
    for k in range(n, n - 12, -1):
        d = hud.get(str(k))
        if d and d["v"] is not None and d["v"] < 1000:
            return d["v"]
    return None


def cream_mask(rgb):
    r, g, b = rgb[..., 0].astype(int), rgb[..., 1].astype(int), rgb[..., 2].astype(int)
    return ((r > 212) & (g > 205) & (b > 185) & (r - b < 40)).astype(np.uint8)


def rect_poly(cx, cy, w, h, th):
    c, s = np.cos(th), np.sin(th)
    pts = np.array([[-w / 2, -h / 2], [w / 2, -h / 2], [w / 2, h / 2], [-w / 2, h / 2]])
    R = np.array([[c, -s], [s, c]])
    return pts @ R.T + [cx, cy]


def fit_falling(mask, U, w, ph):
    """mask: cream mask (window coords). U: (ux0,uy0,ux1,uy1) underlying page in window coords.
    Returns (cx, cy, theta, score) of the falling page or None."""
    ux0, uy0, ux1, uy1 = U
    out = mask.copy()
    out[max(0, int(uy0) - 2):int(uy1) + 3, max(0, int(ux0) - 2):int(ux1) + 3] = 0
    out = cv2.morphologyEx(out, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    if out.sum() < 0.02 * w * ph:
        return None
    ys, xs = np.nonzero(out)
    full = mask.copy()
    full[int(uy0):int(uy1) + 1, int(ux0):int(ux1) + 1] = 1
    best = None
    for th_deg in np.arange(-4, 75, 0.75):
        th = np.deg2rad(th_deg)
        c, s = np.cos(th), np.sin(th)
        # local coords (rotate points by -th)
        lx = xs * c + ys * s
        ly = -xs * s + ys * c
        left, bottom = np.percentile(lx, 1), np.percentile(ly, 99)
        # page in local frame: [left, left+w] x [bottom-ph, bottom]
        lcx, lcy = left + w / 2, bottom - ph / 2
        cx, cy = lcx * c - lcy * s, lcx * s + lcy * c
        poly = rect_poly(cx, cy, w, ph, th)
        model = np.zeros_like(mask)
        cv2.fillPoly(model, [np.round(poly).astype(np.int32)], 1)
        model[int(uy0):int(uy1) + 1, int(ux0):int(ux1) + 1] = 1
        inter = (model & full).sum(); uni = (model | full).sum()
        sc = inter / max(uni, 1)
        if best is None or sc > best[3]:
            best = (cx, cy, th, sc)
    return best


cap = cv2.VideoCapture(video)
cap.set(cv2.CAP_PROP_POS_FRAMES, F0)
res = {}
last_shown = None
cycle_val = None
in_cycle = False
prev_has_cal = False
prev_theta = None
for n in range(F0, F1):
    ok, f = cap.read()
    if not ok:
        break
    hb = cal.get(str(n))
    if not hb:
        in_cycle = False; prev_has_cal = False; last_shown = None; prev_theta = None
        continue
    x0, x1, y0, y1 = hb
    w = x1 - x0 + 1
    ph = PAGE_H * w
    rgb = f[..., ::-1]
    wx0, wx1 = max(0, int(x0 - 1.3 * w)), min(1080, int(x1 + 0.7 * w))
    wy0, wy1 = max(0, int(y0 - 0.3 * w)), min(1920, int(y1 + 2.2 * w))
    win = rgb[wy0:wy1, wx0:wx1]
    mask = cream_mask(win)
    U = (x0 - wx0, y1 + 1 - wy0, x1 - wx0, y1 + ph - wy0)
    digits_visible = dig.get(str(n), 0) > 0.04
    fp = fit_falling(mask, U, w, ph)
    hv = hud_val(n)
    if not prev_has_cal:
        # scene just started: before the first visible number assume previous day
        last_shown = None
    cover_upright = (fp is None) and (not digits_visible)
    has_cover = (fp is not None and fp[3] > 0.80) or cover_upright
    cur_theta = (float(np.rad2deg(fp[2])) if (fp is not None and fp[3] > 0.80) else 0.0) if has_cover else None
    new_cycle = has_cover and in_cycle and prev_theta is not None and cur_theta < prev_theta - 8
    if has_cover and (not in_cycle or new_cycle):
        cycle_val = last_shown if last_shown is not None else (hv - 1 if hv else None)
        in_cycle = True
    if not has_cover:
        in_cycle = False
    prev_theta = cur_theta
    entry = {"hdr": hb, "w": w, "digits": bool(digits_visible), "hud": hv}
    if has_cover and cycle_val is not None:
        if fp is not None and fp[3] > 0.80:
            cx, cy, th, sc = fp
            entry["fall"] = {"cx": round(cx + wx0, 2), "cy": round(cy + wy0, 2), "theta": round(float(np.rad2deg(th)), 2),
                             "score": round(float(sc), 3), "w": w, "h": round(ph, 2), "val": int(cycle_val)}
        else:
            entry["fall"] = {"cx": round((x0 + x1) / 2 + 0.5, 2), "cy": round(y1 + 1 + ph / 2, 2), "theta": 0.0,
                             "score": 1.0, "w": w, "h": round(ph, 2), "val": int(cycle_val), "upright": True}
    if digits_visible and hv is not None:
        last_shown = hv
    prev_has_cal = True
    res[n] = entry
json.dump(res, open(out_p, "w"))
falls = [k for k, v in res.items() if "fall" in v]
print("frames", len(res), "with falling/cover page", len(falls))
low = [(k, res[k]["fall"]["score"]) for k in falls if res[k]["fall"]["score"] < 0.9]
print("low-score fits:", low[:20])
