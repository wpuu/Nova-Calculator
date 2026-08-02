#!/usr/bin/env python3
"""Pixel-level verification of hollow reticle: scan radial profile.
HOLLOW ring + center dot => colored-pixel density is ~0 across the interior,
spikes ONLY at the outer ring (r~R) and at the tiny center (r~0).
FILLED disc => density stays high from center out to edge.
"""
from PIL import Image
import math

IMG = r"E:/Nova Calculator/autoclicker_verify/scr_v3.png"
im = Image.open(IMG).convert("RGB")
W, H = im.size
px = im.load()
print(f"Image size: {W}x{H}")

def is_red(r, g, b):
    return r > 140 and g < 110 and b < 110 and (r - max(g, b)) > 60

def is_blue(r, g, b):
    return b > 140 and r < 130 and g < 180 and (b - max(r, g)) > 50

def collect(name):
    pts = []
    for y in range(0, H, 2):
        for x in range(0, W, 2):
            r, g, b = px[x, y]
            if (name == "RED" and is_red(r, g, b)) or (name == "BLUE" and is_blue(r, g, b)):
                pts.append((x, y))
    return pts

def profile(name, pts):
    if len(pts) < 20:
        print(f"  [{name}] too few pixels"); return
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    cx = (min(xs) + max(xs)) / 2.0
    cy = (min(ys) + max(ys)) / 2.0
    R = max(max(xs) - min(xs), max(ys) - min(ys)) / 2.0
    print(f"  [{name}] center=({cx:.0f},{cy:.0f}) R~{R:.0f}px  colored_px={len(pts)}")

    # bin density by normalized radius t = d/R, step 0.1
    bins = [0] * 11          # count of colored px in each t-bin
    areas = [0] * 11         # count of ALL px sampled in each t-bin
    step = 2
    for y in range(int(cy - R), int(cy + R) + 1, step):
        for x in range(int(cx - R), int(cx + R) + 1, step):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d > R:
                continue
            t = d / R
            bi = min(10, int(t * 10))
            areas[bi] += 1
            r, g, b = px[x, y]
            colored = is_red(r, g, b) if name == "RED" else is_blue(r, g, b)
            if colored:
                bins[bi] += 1
    print("  radial density (t=0 center -> t=1 edge | colored/total):")
    for bi in range(11):
        dens = bins[bi] / areas[bi] if areas[bi] else 0
        bar = "#" * int(dens * 40)
        print(f"    t={bi/10:.1f}  {dens:5.2f}  {bar}")
    # Decision:
    # hollow => interior bins (t=0..0.5) are low, edge bin (t=0.9..1.0) is high
    interior = sum(bins[0:5]) / max(1, sum(areas[0:5]))
    edge = bins[10] / max(1, areas[10])
    print(f"  interior density(t<0.5)={interior:.3f}  edge density(t~1.0)={edge:.3f}")
    if interior < 0.25 and edge > 0.4:
        print("  VERDICT: HOLLOW ring + center dot  ✅")
    elif interior > 0.5:
        print("  VERDICT: FILLED solid disc  ❌")
    else:
        print("  VERDICT: UNCLEAR")

for name in ("RED", "BLUE"):
    print(f"=== {name} circle ===")
    profile(name, collect(name))
