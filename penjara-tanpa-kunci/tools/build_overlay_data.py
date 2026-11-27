"""Build the per-frame JSON data used by the Remotion overlay project (remotion/src/data/*).

Inputs come from the analysis scripts:
  hud.json            per-frame HUD value/opacity read from the v3 render (hud_track.py)
  calendar_fit.json   falling calendar page pose per frame (calendar_fit.py)
  door_caption_raw    caption text extents in the door close-up
"""
import json, sys, os

an, out = sys.argv[1], sys.argv[2]
hud = json.load(open(os.path.join(an, "hud.json")))
cal = json.load(open(os.path.join(an, "calendar_fit.json")))
door = json.load(open(os.path.join(an, "door_caption_raw.json")))

# ---------------- HUD ("JANJI 'BESOK'") ----------------
SEGMENTS = [(248, 929), (1032, 1304), (1344, 1450), (1482, 1816), (1875, 2315),
            (3070, 3515), (3567, 3947), (4005, 4127), (4242, 4320)]
FADE_IN = {248: 0.0, 249: 0.26, 250: 0.54, 251: 0.76, 252: 0.98}
XFADE = (4310, 4320)   # hand back to the original (small) HUD right before it flies to the centre


def value(n):
    for k in range(n, n - 15, -1):
        d = hud.get(str(k))
        if d and d["v"] is not None and d["v"] < 1000:
            return d["v"]
    return None


frames = {}
last = None
for a, b in SEGMENTS:
    for n in range(a, b + 1):
        v = value(n)
        if v is None:
            v = last
        if last is not None and v is not None and v < last and a <= 929:   # OCR glitch guard in the count-up
            v = last
        last = v
        alpha = FADE_IN.get(n, 1.0)
        if XFADE[0] <= n <= XFADE[1]:
            alpha = 1.0 - (n - XFADE[0] + 1) / (XFADE[1] - XFADE[0] + 2)
        frames[n] = [round(alpha, 3), v]
# fill the 8-frame OCR gap (653-659 -> 8) explicitly
for n in range(653, 660):
    frames[n] = [1.0, 8]
json.dump(frames, open(os.path.join(out, "hud.json"), "w"), separators=(",", ":"))

# ---------------- calendar: number on the torn-off page ----------------
cal_out = {}
for k, e in cal.items():
    f = e.get("fall")
    if not f:
        continue
    n = int(k)
    if f["val"] is None or e["hud"] is None:
        continue
    if f["val"] >= e["hud"]:          # last cycle (367 -> 367): nothing to tear, keep original
        continue
    cal_out[n] = [round(f["cx"], 2), round(f["cy"], 2), round(f["theta"], 2), f["w"], round(f["h"], 2), f["val"]]
json.dump(cal_out, open(os.path.join(out, "calendar.json"), "w"), separators=(",", ":"))

# ---------------- door close-up caption pills (bar collision) ----------------
PAD = 41
pills = {}
pages = [((4614, 4648), (174, 908)), ((4649, 4691), (309, 770))]
for (a, b), (tx0, tx1) in pages:
    for n in range(a, b + 1):
        pills[n] = [tx0 - PAD, tx1 + PAD, 1459, 1559, n - a]
json.dump(pills, open(os.path.join(out, "doorCaption.json"), "w"), separators=(",", ":"))

print("hud frames", len(frames), "calendar frames", len(cal_out), "door pills", len(pills))
