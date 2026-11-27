"""Re-check every QA finding on the final render. Usage: verify_final.py <v3.mp4> <v4.mp4> <outdir>
Writes side-by-side crops (v3 | v4) per finding and prints numeric checks."""
import sys, os
import numpy as np, cv2

v3p, v4p, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)
a, b = cv2.VideoCapture(v3p), cv2.VideoCapture(v4p)
print("frames", a.get(7), b.get(7))


def fr(cap, n):
    cap.set(cv2.CAP_PROP_POS_FRAMES, n)
    return cap.read()[1]


def sheet(name, frames, box, scale=1.0, contrast=False):
    y0, y1, x0, x1 = box
    rows = []
    for n in frames:
        A, B = fr(a, n)[y0:y1, x0:x1], fr(b, n)[y0:y1, x0:x1]
        if contrast:
            A = np.clip((A.astype(np.float32) - A.mean((0, 1))) * 3 + 128, 0, 255).astype(np.uint8)
            B = np.clip((B.astype(np.float32) - B.mean((0, 1))) * 3 + 128, 0, 255).astype(np.uint8)
        t = np.hstack([A, np.full((y1 - y0, 6, 3), 255, np.uint8), B])
        t = cv2.resize(t, None, fx=scale, fy=scale)
        cv2.putText(t, str(n), (4, 18), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)
        rows.append(t)
    cv2.imwrite(os.path.join(out, name + ".jpg"), np.vstack(rows))


# 1 calendar: frames in the montage where no dark digit pixels exist on/around the calendar
blank = []
b.set(cv2.CAP_PROP_POS_FRAMES, 555)
for n in range(555, 930):
    f = b.read()[1].astype(int)
    r, g, bl = f[..., 2], f[..., 1], f[..., 0]
    red = (r > 170) & (g < 110) & (bl < 110)
    ys, xs = np.nonzero(red[:1000])
    if len(xs) < 300:
        continue
    x0, x1, y1 = xs.min(), xs.max(), ys.max()
    w = x1 - x0
    reg = f[y1 + 5:y1 + int(1.6 * w), max(0, x0 - int(0.8 * w)):x1 + int(0.3 * w)]
    cream = (reg.mean(-1) > 200)
    dark = (reg.mean(-1) < 90) & (np.abs(reg[..., 0] - reg[..., 2]) < 30)
    # digits sit inside cream pages: dark pixels with cream around them
    dk = cv2.dilate(dark.astype(np.uint8), np.ones((9, 9), np.uint8)).astype(bool) & cream
    if dk.sum() < 40:
        blank.append(n)
print("calendar frames without any visible digit:", blank)
sheet("cal", [648, 654, 666, 672, 678, 696, 918, 924, 926], (380, 640, 300, 760), 0.8)
# 2 HUD vs calendar
sheet("hud_cal", [405, 412, 419, 905, 915, 926], (60, 470, 360, 1080), 0.7)
# 3 bubble edge
sheet("bubble", [1153, 1180, 1230, 1273], (280, 620, 200, 700), 0.9)
# 4 ghost text
sheet("ghost", list(range(4326, 4332)), (560, 800, 320, 960), 0.8)
# 5 shadow: tail of the first city shot, 3008, notes segment, end segment (contrast-stretched)
sheet("wedge_tail", [2500, 2506, 2512, 2518, 2524, 2530, 2536], (900, 1350, 0, 760), 0.6, contrast=True)
sheet("wedge_misc", [3007, 3008, 4986, 4988, 4990, 5653, 5656, 5659], (880, 1360, 300, 1080), 0.5, contrast=True)
# 6 HUD entrance and hand-over
sheet("hud_in_out", [246, 247, 248, 249, 4316, 4318, 4319, 4320], (150, 450, 560, 1080), 0.7)
# 7 door outline end, payoff, hook
sheet("door", [4686, 4688, 4689, 4690], (1380, 1640, 40, 1040), 0.6)
sheet("payoff", [5598, 5599, 5636, 5637, 5646, 5700], (0, 1100, 0, 1080), 0.35)
sheet("hook", [0, 52, 76, 77], (0, 1920, 0, 1080), 0.25)
# 8 untouched areas: YUV-equivalent brightness check (mean abs diff outside overlays)
d = []
for n in (1000, 2700, 4000, 5300):
    A, B = fr(a, n).astype(int), fr(b, n).astype(int)
    d.append(np.abs(A[700:1400] - B[700:1400]).mean())
print("mean |v3-v4| in untouched band:", [round(x, 2) for x in d])
