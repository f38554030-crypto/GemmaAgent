"""Ground the cube in the city shots ("kubus melayang").

v3 draws the cube in an oblique view: the bottom edge of its side face rises from the sidewalk to the back
corner, ~100 px above the sidewalk, while the street is drawn flat (side view). Trees and facades show
through under that edge, so the cube reads as floating. Fix per frame (RGB ROI):
  1. extend the side face down to the sidewalk line (gap filled with the side-face colour sampled in the
     same frame, so fades stay correct); the fill overlaps the old edge by 2.5 px so no seam remains,
  2. foreground in front of the cube (main figure, its legs, flying notes) is kept; its anti-aliased rim is
     re-composited over the new side colour (no background halo),
  3. extend the cube's drop shadow on the sidewalk so it also lies under the extended side.
"""
import numpy as np, cv2

ALPHA = 0.125   # the sidewalk drop shadow is a 12.5 % black overlay
K = 1.27        # dx/dy of the shadow's slanted right end
SS = 8
SIDE_REF = np.array([17.0, 23.0, 40.0])


def poly_cov(shape, poly, ss=SS):
    H, W = shape
    x0 = max(0, int(np.floor(poly[:, 0].min())) - 2); x1 = min(W, int(np.ceil(poly[:, 0].max())) + 2)
    y0 = max(0, int(np.floor(poly[:, 1].min())) - 2); y1 = min(H, int(np.ceil(poly[:, 1].max())) + 2)
    if x1 <= x0 or y1 <= y0:
        return None
    big = np.zeros(((y1 - y0) * ss, (x1 - x0) * ss), np.uint8)
    pts = np.round((poly - [x0, y0]) * ss * 16).astype(np.int32)
    cv2.fillPoly(big, [pts], 255, lineType=cv2.LINE_8, shift=4)
    cov = cv2.resize(big, (x1 - x0, y1 - y0), interpolation=cv2.INTER_AREA).astype(np.float32) / 255.0
    full = np.zeros(shape, np.float32)
    full[y0:y1, x0:x1] = cov
    return full


def measure(rgb, g):
    """Per-frame measurements. Returns dict with any of: y_ground (sidewalk top under the front face),
    y_shadow (bottom of the drop-shadow band), side (side-face colour)."""
    H, W = rgb.shape[:2]
    lum = rgb.astype(np.float32).mean(-1)
    out = {}
    tops, bots = [], []
    y_start = int(g["fbr_y"]) - 6
    for x in np.linspace(g["fbr_x"] - 120, g["fbr_x"] - 15, 8):
        x = int(x)
        if not (0 <= x < W) or y_start < 0:
            continue
        col = lum[y_start:min(H, y_start + 90), x]
        if len(col) < 40:
            continue
        d = np.diff(col)
        i = int(np.argmax(d[:30]))                         # cube front face -> sidewalk
        if d[i] < 0.25 * max(col.max(), 1.0):
            continue
        top = y_start + i + 1
        base = col[i + 1:i + 6].mean()                     # shadowed sidewalk level
        lit = np.nonzero(col[i + 1:] > base / (1 - ALPHA) * 0.97)[0]
        tops.append(top)
        if len(lit):
            bots.append(top + lit[0])
    if tops:
        out["y_ground"] = float(np.median(tops))
    if bots:
        out["y_shadow"] = float(np.median(bots))
    samples = []
    for x in np.linspace(g["fbr_x"] + 6, g["bbr_x"] - 6, 12):
        yi, xi = int(g["fbr_y"] + g["m"] * (x - g["fbr_x"])) - 7, int(x)
        if 0 <= yi < H and 0 <= xi < W:
            samples.append(rgb[yi, xi].astype(np.float32))
    if len(samples) >= 3:
        out["side"] = np.median(np.array(samples), axis=0)
    return out


def _nearest(values, valid):
    """For every pixel, the value of the nearest pixel where valid is True."""
    src = (~valid).astype(np.uint8)
    _, labels = cv2.distanceTransformWithLabels(src, cv2.DIST_L2, 3, labelType=cv2.DIST_LABEL_PIXEL)
    ys, xs = np.nonzero(valid)
    lut_y = np.zeros(labels.max() + 1, np.int32); lut_x = np.zeros(labels.max() + 1, np.int32)
    lut_y[labels[ys, xs]] = ys; lut_x[labels[ys, xs]] = xs
    return values[lut_y[labels], lut_x[labels]]


def apply(rgb, g, y_ground, y_shadow, side, alpha=1.0, protect_fg=False):
    """rgb: HxWx3 uint8 ROI. Returns modified uint8 copy."""
    H, W = rgb.shape[:2]
    img = rgb.astype(np.float32)
    side = np.asarray(side, np.float32)
    bscale = max(float(side.mean() / SIDE_REF.mean()), 0.05)          # brightness during fades
    # 1) side face down to the sidewalk
    poly = np.array([[g["fbr_x"], g["fbr_y"] - 2.5], [g["bbr_x"], g["bbr_y"] - 2.5],
                     [g["bbr_x"], y_ground], [g["fbr_x"], y_ground]], np.float64)
    cov = poly_cov((H, W), poly)
    if cov is not None and cov.max() > 0:
        comp = np.broadcast_to(side, img.shape).copy()
        if protect_fg:
            lum = img.mean(-1); sat = img.max(-1) - img.min(-1)
            white = (lum > 185 * bscale) & (sat < 40 * bscale + 4)
            legs = (lum < 32 * bscale + 2) & (np.abs(img - side).max(-1) > 6 * bscale + 1.5)
            notes = (img[..., 0] > 170 * bscale) & (img[..., 1] > 130 * bscale) & (img[..., 2] < 130 * bscale + 10) \
                & (img[..., 0] - img[..., 2] > 50 * bscale)
            core = (white | legs | notes) & (cov > 0)
            core = cv2.morphologyEx(core.astype(np.uint8), cv2.MORPH_OPEN, np.ones((2, 2), np.uint8)).astype(bool)
            if core.any():
                ring = cv2.dilate(core.astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))).astype(bool) & ~core
                inside = cov > 0.99
                valid_bg = inside & ~core & ~ring
                if valid_bg.any():
                    fg = _nearest(img, core)
                    bg = _nearest(img, valid_bg)
                    dvec = fg - bg
                    a = np.clip(((img - bg) * dvec).sum(-1) / np.maximum((dvec * dvec).sum(-1), 1e-3), 0, 1)[..., None]
                    rim = a * fg + (1 - a) * side
                    comp[ring] = rim[ring]
                comp[core] = img[core]
        c = (cov * alpha)[..., None]
        img = img * (1 - c) + comp * c
    # 2) drop shadow on the sidewalk under the extended side
    shift = g["bbr_x"] - g["fbr_x"]
    def edge_x(y, gx):
        return gx - (y - g["fbr_y"]) * K
    gx_old, gx_new = g["gr_x"], g["gr_x"] + shift
    sp = np.array([[edge_x(y_ground, gx_old), y_ground], [edge_x(y_ground, gx_new), y_ground],
                   [edge_x(y_shadow, gx_new), y_shadow], [edge_x(y_shadow, gx_old), y_shadow]], np.float64)
    scov = poly_cov((H, W), sp)
    if scov is not None and scov.max() > 0:
        # reference = unshadowed sidewalk right next to the old shadow end (same frame -> fades are fine)
        yr = int(round((y_ground + y_shadow) / 2)); xr = int(round(edge_x(yr, gx_old) + 6))
        if 0 <= yr < H and 0 <= xr < W - 3:
            ref = np.median(img[yr - 1:yr + 2, xr:xr + 3].reshape(-1, 3), axis=0)
            sidewalk = np.abs(img - ref).max(-1) < 14 * max(bscale, 0.3)
            s = (scov * sidewalk * alpha)[..., None]
            img = img * (1 - ALPHA * s)
    return np.clip(img + 0.5, 0, 255).astype(np.uint8)
