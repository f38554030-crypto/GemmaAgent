"""Remove the cube's cast-shadow wedge that spills onto the background above the sidewalk.

The original draws the cube's ground shadow as a parallelogram receding up-right, but the
street is a flat side view, so the part above the sidewalk darkens trees/buildings behind the
cube (reads as a dark glass panel).  Shadow = black overlay with alpha 0.125.

Per frame:
  1. find the cube side face (RGB 17,23,40) and fit its bottom diagonal  -> FBR, BBR
  2. predict the shadow polygon from measured ratios, then refine its top edge (horizontal)
     and right edge (straight line) from the 12.5 % luminance step in the image itself
  3. divide pixels inside the polygon (above the ground line) by 0.875, anti-aliased,
     skipping foreground drawn on top of the shadow (figure, legs, notes, cube).
"""
import numpy as np, cv2

SIDE = np.array([17, 23, 40])
ALPHA = 0.125
RIGHT_SLOPE = 1.27   # dx/dy magnitude of the shadow's right edge
GR_RATIO = 0.66      # ground crossing of right edge = FBR.x + GR_RATIO * side_width
SS = 8


def detect(rgb):
    side = (np.abs(rgb.astype(np.int16) - SIDE).max(-1) <= 6).astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(side, 8)
    if n < 2:
        return None
    # caption pills / end card can take a similar colour over bright backgrounds:
    # test the biggest few components and keep the one whose bottom edge is the cube diagonal
    order = 1 + np.argsort(-stats[1:, cv2.CC_STAT_AREA])[:4]
    for k in order:
        if stats[k, cv2.CC_STAT_AREA] < 4000 or stats[k, cv2.CC_STAT_HEIGHT] < 150:
            continue
        sm = lab == k
        ys, xs = np.nonzero(sm)
        x0, x1 = int(xs.min()), int(xs.max())
        if x1 - x0 < 30:
            continue
        cols = np.arange(x0 + 1, x0 + int(0.85 * (x1 - x0)) + 1)
        bot = np.array([np.nonzero(sm[:, c])[0].max() for c in cols], dtype=float)
        A = np.vstack([cols, np.ones_like(cols)]).T
        keep = np.ones(len(cols), bool)
        for _ in range(3):                      # robust refit: drop occluded columns (walking figure etc.)
            m, c = np.linalg.lstsq(A[keep], bot[keep], rcond=None)[0]
            resid = np.abs(A @ [m, c] - bot)
            keep = resid < max(2.0, 2.5 * np.median(resid[keep]))
        if not (-0.80 < m < -0.66) or keep.mean() < 0.6 or np.median(resid[keep]) > 1.5:
            continue
        fbr = (float(x0), m * x0 + c + 0.5)
        bbr = (float(x1) + 0.5, m * (x1 + 0.5) + c + 0.5)
        return fbr, bbr, m
    return None


def _step(profile, i_mid, half=6):
    """Subpixel position of a 12.5% darkening step in 1-D luminance profile (bright -> dark)."""
    a = profile[i_mid - half:i_mid - 2]
    b = profile[i_mid + 3:i_mid + half + 1]
    if len(a) < 3 or len(b) < 3:
        return None
    u, d = a.mean(), b.mean()
    if u < 25 or a.std() > 2.5 or b.std() > 2.5:
        return None
    if abs(d / u - (1 - ALPHA)) > 0.035:
        return None
    mid = (u + d) / 2
    seg = profile[i_mid - 3:i_mid + 4]
    for j in range(len(seg) - 1):
        if seg[j] >= mid > seg[j + 1]:
            return (i_mid - 3 + j) + (seg[j] - mid) / (seg[j] - seg[j + 1]) + 0.5
    return None


def refine(lum, fbr, bbr):
    H, W = lum.shape
    yg = fbr[1]
    w = bbr[0] - fbr[0]
    # --- top edge (horizontal, nominally at bbr.y) ---
    gr_x = fbr[0] + GR_RATIO * w
    tr_x = gr_x + (yg - bbr[1]) * RIGHT_SLOPE
    yp = int(round(bbr[1]))
    tops = []
    for x in range(int(bbr[0]) + 4, min(W - 1, int(tr_x) - 4)):
        p = lum[max(0, yp - 12):yp + 13, x]
        if len(p) < 25:
            continue
        for off in range(-3, 6):
            s = _step(p, 12 + off)
            if s is not None:
                tops.append(yp - 12 + s)
                break
    y_top = float(np.median(tops)) if len(tops) >= 5 else bbr[1] + 2.0
    # --- right edge (line x = xg + (yg - y) * slope) ---
    pts = []
    for y in range(int(y_top) + 4, int(yg) - 3):
        xp = gr_x + (yg - y) * RIGHT_SLOPE
        xi = int(round(xp))
        if xi - 16 < 0 or xi + 16 >= W:
            continue
        p = lum[y, xi - 16:xi + 17][::-1]          # reversed: bright (outside) -> dark (inside)
        for off in range(-4, 8):
            s = _step(p, 16 - off)
            if s is not None:
                x_edge = xi + 16 - s + 1.0
                pts.append((y, x_edge))
                break
    if len(pts) >= 6:
        P = np.array(pts)
        offs = P[:, 1] - (gr_x + (yg - P[:, 0]) * RIGHT_SLOPE)
        gr_x = gr_x + float(np.median(offs))
    tr_x = gr_x + (yg - y_top) * RIGHT_SLOPE
    return y_top, gr_x, tr_x, len(tops), len(pts)


def polygon_mask(shape, poly, ss=SS):
    H, W = shape
    x0 = max(0, int(np.floor(poly[:, 0].min())) - 3); x1 = min(W, int(np.ceil(poly[:, 0].max())) + 3)
    y0 = max(0, int(np.floor(poly[:, 1].min())) - 3); y1 = min(H, int(np.ceil(poly[:, 1].max())) + 3)
    if x1 <= x0 or y1 <= y0:
        return None
    big = np.zeros(((y1 - y0) * ss, (x1 - x0) * ss), np.uint8)
    pts = np.round((poly - [x0, y0]) * ss * 16).astype(np.int32)
    cv2.fillPoly(big, [pts], 255, lineType=cv2.LINE_8, shift=4)
    cov = cv2.resize(big, (x1 - x0, y1 - y0), interpolation=cv2.INTER_AREA).astype(np.float32) / 255.0
    return (x0, y0, x1, y1), cov


def fix(rgb):
    det = detect(rgb)
    if det is None:
        return rgb, None
    fbr, bbr, m = det
    lum = rgb.astype(np.float32).mean(-1)
    y_top, gr_x, tr_x, nt, nr = refine(lum, fbr, bbr)
    yg = fbr[1]
    # left boundary: the diagonal, extended up to y_top
    x_at_top = fbr[0] + (y_top - fbr[1]) / m
    poly = np.array([[fbr[0] - 1.0, yg], [x_at_top - 1.0, y_top], [tr_x, y_top], [gr_x, yg]])
    res = polygon_mask(rgb.shape[:2], poly)
    if res is None:
        return rgb, None
    (x0, y0, x1, y1), cov = res
    yy = np.arange(y0, y1)[:, None] + 0.5
    cov *= np.clip(yg - yy + 0.5, 0, 1)            # only above the ground line
    roi = rgb[y0:y1, x0:x1].astype(np.float32)
    mx, mn = roi.max(-1), roi.min(-1)
    l = roi.mean(-1)
    protect = ((l > 185) & (mx - mn < 35)) | (l < 30) | \
              ((roi[..., 0] > 190) & (roi[..., 1] > 150) & (roi[..., 2] < 130)) | \
              (np.abs(roi - SIDE).max(-1) <= 6)
    cov *= ~protect
    gain = 1.0 / (1.0 - ALPHA * cov[..., None])
    out = rgb.copy()
    out[y0:y1, x0:x1] = np.clip(roi * gain + 0.5, 0, 255).astype(np.uint8)
    info = dict(fbr=[round(fbr[0], 1), round(fbr[1], 1)], bbr=[round(bbr[0], 1), round(bbr[1], 1)],
                y_top=round(y_top, 2), gr_x=round(gr_x, 2), tr_x=round(tr_x, 2), n_top=nt, n_right=nr)
    return out, info


if __name__ == "__main__":
    import sys
    for p in sys.argv[1:]:
        bgr = cv2.imread(p)
        out, info = fix(bgr[..., ::-1].copy())
        print(p.split('/')[-1], info)
        cv2.imwrite(p.replace('.png', '_fixed.png'), out[..., ::-1])
