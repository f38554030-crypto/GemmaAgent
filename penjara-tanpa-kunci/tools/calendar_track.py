"""Track the wall calendar (red 'HARI' header) per frame.
Output JSON {frame: [x0, x1, y0, y1]} of the red header bbox (None when not found).
"""
import sys, json
import numpy as np, cv2

video, out = sys.argv[1], sys.argv[2]
ranges = [(240, 950), (1020, 1320)]
cap = cv2.VideoCapture(video)
res = {}
for a, b in ranges:
    cap.set(cv2.CAP_PROP_POS_FRAMES, a)
    for n in range(a, b):
        ok, f = cap.read()
        if not ok:
            break
        rgb = f[:1000].astype(np.int16)[..., ::-1]
        r, g, bl = rgb[..., 0], rgb[..., 1], rgb[..., 2]
        m = ((r > 170) & (g < 110) & (bl < 110)).astype(np.uint8)
        nlab, lab, st, _ = cv2.connectedComponentsWithStats(m, 8)
        best = None
        for k in range(1, nlab):
            x, y, w, h, area = st[k]
            if area < 300 or h < 8:
                continue
            ar = w / max(h, 1)
            if not (2.4 < ar < 5.5):
                continue
            if best is None or area > best[4]:
                best = (x, y, w, h, area)
        res[n] = None if best is None else [int(best[0]), int(best[0] + best[2] - 1), int(best[1]), int(best[1] + best[3] - 1)]
json.dump(res, open(out, "w"))
print("frames", len(res), "found", sum(v is not None for v in res.values()))
