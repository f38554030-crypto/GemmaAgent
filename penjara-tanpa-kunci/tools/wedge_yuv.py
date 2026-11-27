"""Shadow-wedge removal done in YUV420 space (matches how the encoder stored the shadow).

fix_yuv(Y, U, V) -> (Y, U, V, info)   Y: HxW uint8, U/V: H/2 x W/2 uint8 (tv range)
"""
import numpy as np, cv2
import wedge_fix as wf

ALPHA = wf.ALPHA


def yuv_to_rgb(Y, U, V):
    H, W = Y.shape
    i420 = np.concatenate([Y.reshape(-1), U.reshape(-1), V.reshape(-1)]).reshape(H * 3 // 2, W)
    return cv2.cvtColor(i420, cv2.COLOR_YUV2RGB_I420)


def chroma_cov(covY, mode="box"):
    H, W = covY.shape
    if mode == "box":
        return covY.reshape(H // 2, 2, W // 2, 2).mean(axis=(1, 3))
    # left-sited (horizontal taps 1/4,1/2,1/4 centred on even column), vertical box
    pad = np.pad(covY, ((0, 0), (1, 1)), mode="edge")
    hor = 0.25 * pad[:, 0:-2] + 0.5 * pad[:, 1:-1] + 0.25 * pad[:, 2:]
    hor = hor[:, 0::2]
    return hor.reshape(H // 2, 2, W // 2).mean(axis=1)


def measure(Y, U, V):
    """Detect cube + refine shadow edges. Returns geometry dict or None."""
    rgb = yuv_to_rgb(Y, U, V)
    det = wf.detect(rgb)
    if det is None:
        return None
    fbr, bbr, m = det
    lum = Y.astype(np.float32) - 16.0
    y_top, gr_x, tr_x, nt, nr = wf.refine(lum, fbr, bbr)
    return dict(fbr_x=float(fbr[0]), fbr_y=float(fbr[1]), bbr_x=float(bbr[0]), bbr_y=float(bbr[1]), m=float(m),
                y_top=float(y_top), gr_x=float(gr_x), tr_x=float(tr_x), n_top=int(nt), n_right=int(nr))


def apply(Y, U, V, g, extra_protect=None):
    H, W = Y.shape
    yg = g["fbr_y"]; m = g["m"]; y_top = g["y_top"]
    x_at_top = g["fbr_x"] + (y_top - yg) / m
    poly = np.array([[g["fbr_x"] - 1.0, yg], [x_at_top - 1.0, y_top], [g["tr_x"], y_top], [g["gr_x"], yg]])
    res = wf.polygon_mask((H, W), poly)
    if res is None:
        return Y, U, V
    (x0, y0, x1, y1), cov = res
    ex0, ey0 = x0 - (x0 % 2), y0 - (y0 % 2)
    ex1, ey1 = min(W, x1 + (x1 % 2)), min(H, y1 + (y1 % 2))
    covY = np.zeros((ey1 - ey0, ex1 - ex0), np.float32)
    covY[y0 - ey0:y1 - ey0, x0 - ex0:x1 - ex0] = cov
    yy = np.arange(ey0, ey1)[:, None] + 0.5
    covY *= np.clip(yg - yy + 0.5, 0, 1)
    rgb = yuv_to_rgb(Y, U, V)
    roi = rgb[ey0:ey1, ex0:ex1].astype(np.float32)
    mx, mn = roi.max(-1), roi.min(-1)
    l = roi.mean(-1)
    protect = ((l > 185) & (mx - mn < 35)) | (l < 30) | \
              ((roi[..., 0] > 190) & (roi[..., 1] > 150) & (roi[..., 2] < 130)) | \
              (np.abs(roi - wf.SIDE).max(-1) <= 6)
    if extra_protect is not None:
        protect |= extra_protect[ey0:ey1, ex0:ex1]
    prot = cv2.dilate(protect.astype(np.uint8), np.ones((3, 3), np.uint8)).astype(np.float32)
    covY *= (1.0 - prot)
    covC = chroma_cov(covY)
    Yo, Uo, Vo = Y.copy(), U.copy(), V.copy()
    ys = Y[ey0:ey1, ex0:ex1].astype(np.float32)
    Yo[ey0:ey1, ex0:ex1] = np.clip(16 + (ys - 16) / (1 - ALPHA * covY) + 0.5, 0, 255).astype(np.uint8)
    cy0, cy1, cx0, cx1 = ey0 // 2, ey1 // 2, ex0 // 2, ex1 // 2
    for P, Po in ((U, Uo), (V, Vo)):
        cs = P[cy0:cy1, cx0:cx1].astype(np.float32)
        Po[cy0:cy1, cx0:cx1] = np.clip(128 + (cs - 128) / (1 - ALPHA * covC) + 0.5, 0, 255).astype(np.uint8)
    return Yo, Uo, Vo


def fix_yuv(Y, U, V, mode="box", extra_protect=None):
    g = measure(Y, U, V)
    if g is None:
        return Y, U, V, None
    Yo, Uo, Vo = apply(Y, U, V, g, extra_protect)
    info = dict(fbr=[round(g["fbr_x"], 1), round(g["fbr_y"], 1)], bbr=[round(g["bbr_x"], 1), round(g["bbr_y"], 1)],
                y_top=round(g["y_top"], 2), gr_x=round(g["gr_x"], 2), tr_x=round(g["tr_x"], 2),
                n_top=g["n_top"], n_right=g["n_right"])
    return Yo, Uo, Vo, info


if __name__ == "__main__":
    import sys
    p, W, H = sys.argv[1], 1080, 1920
    raw = np.fromfile(p, np.uint8)
    Y = raw[:W * H].reshape(H, W); U = raw[W * H:W * H * 5 // 4].reshape(H // 2, W // 2); V = raw[W * H * 5 // 4:].reshape(H // 2, W // 2)
    for mode in ("box", "left"):
        Yo, Uo, Vo, info = fix_yuv(Y, U, V, mode)
        print(mode, info)
        a = yuv_to_rgb(Y, U, V).astype(int); b = yuv_to_rgb(Yo, Uo, Vo).astype(int)
        cv2.imwrite(p.replace('.yuv', f'_{mode}.png'), b[..., ::-1].astype(np.uint8))
        cv2.imwrite(p.replace('.yuv', '_orig.png'), a[..., ::-1].astype(np.uint8))
        gr_x, yg = info['gr_x'], info['fbr'][1]
        for y in (1150, 1200, 1250):
            xe = gr_x + (yg - y) * 1.27; xi = int(xe)
            print(f'  y{y} edge~{xe:.1f} after R:', b[y, xi-5:xi+6, 0].tolist(), ' B:', b[y, xi-5:xi+6, 2].tolist())
        for x in (420, 450):
            print(f'  x{x} top after B:', b[1092:1104, x, 2].tolist())
