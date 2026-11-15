"""Draws the launch scenes as layered SVG (design/splash.md): app/src/main/assets/splash/<name>.svg.

Run from the repo root:
    python3 tools/design/splash_vector.py              # the public scenes
    python3 tools/design/splash_vector.py --preview    # also build/splash-preview/<name>.svg with real blur, for a browser

Only the subset of SVG that the app reads is written: one level of <g> layers, <path>, user-space gradients.
The scenes drawn from the owner's photos live in splash_vector_private.py (never in git) and use this module.
"""
import math
import random
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/assets/splash"
W, H = 1080, 2400

# design/splash-icon-context.txt, points 2 and 3. Nothing outside this list may appear in a scene.
NIGHT, DEEP, SHADE, ACTION, MID, LIGHT, WHITE = "#00143E", "#000E2C", "#002864", "#0047A0", "#468CE1", "#BFD9FF", "#FFFFFF"
B1, B2, B3, B4, B5, B6 = "#001F5C", "#002F75", "#2F6FC4", "#5C9BE6", "#8FC4F7", "#E6F1FF"
RED, GOLD, PINK, LAV, PEACH = "#E5484D", "#FFD27A", "#F58FB0", "#9C8FE0", "#FFC9A3"
BLUES = {NIGHT, DEEP, SHADE, ACTION, MID, LIGHT, WHITE, B1, B2, B3, B4, B5, B6}
MAX_PATHS, MAX_LAYERS, MAX_BYTES = 700, 16, 250_000


def f(v):
    s = f"{v:.1f}"
    return s[:-2] if s.endswith(".0") else s


# ---- path builders: every one returns the text of a "d" attribute ----

def circle(cx, cy, r):
    return f"M{f(cx - r)} {f(cy)}a{f(r)} {f(r)} 0 1 0 {f(2 * r)} 0a{f(r)} {f(r)} 0 1 0 {f(-2 * r)} 0Z"


def ellipse(cx, cy, rx, ry):
    return f"M{f(cx - rx)} {f(cy)}a{f(rx)} {f(ry)} 0 1 0 {f(2 * rx)} 0a{f(rx)} {f(ry)} 0 1 0 {f(-2 * rx)} 0Z"


def rect(x, y, w, h, r=0):
    if r <= 0:
        return f"M{f(x)} {f(y)}h{f(w)}v{f(h)}h{f(-w)}Z"
    r = min(r, w / 2, h / 2)
    return (f"M{f(x + r)} {f(y)}h{f(w - 2 * r)}a{f(r)} {f(r)} 0 0 1 {f(r)} {f(r)}v{f(h - 2 * r)}a{f(r)} {f(r)} 0 0 1 {f(-r)} {f(r)}"
            f"h{f(-(w - 2 * r))}a{f(r)} {f(r)} 0 0 1 {f(-r)} {f(-r)}v{f(-(h - 2 * r))}a{f(r)} {f(r)} 0 0 1 {f(r)} {f(-r)}Z")


def poly(pts, close=True):
    return "M" + "L".join(f"{f(x)} {f(y)}" for x, y in pts) + ("Z" if close else "")


def smooth(pts, tension=0.5, start=True):
    """A Catmull-Rom curve through pts as cubic Béziers (no closing)."""
    out = [f"M{f(pts[0][0])} {f(pts[0][1])}"] if start else []
    for i in range(len(pts) - 1):
        p0, p1, p2, p3 = pts[max(i - 1, 0)], pts[i], pts[i + 1], pts[min(i + 2, len(pts) - 1)]
        c1 = (p1[0] + (p2[0] - p0[0]) * tension / 3, p1[1] + (p2[1] - p0[1]) * tension / 3)
        c2 = (p2[0] - (p3[0] - p1[0]) * tension / 3, p2[1] - (p3[1] - p1[1]) * tension / 3)
        out.append(f"C{f(c1[0])} {f(c1[1])} {f(c2[0])} {f(c2[1])} {f(p2[0])} {f(p2[1])}")
    return "".join(out)


def ridge(pts, bottom, tension=0.5):
    """A mountain: the smooth line through pts, closed down to y = bottom."""
    return smooth(pts, tension) + f"L{f(pts[-1][0])} {f(bottom)}L{f(pts[0][0])} {f(bottom)}Z"


def sample(pts, step=18, tension=0.5):
    """Points every ~step px along the smooth line through pts (for tree lines and facets)."""
    out = []
    for i in range(len(pts) - 1):
        p0, p1, p2, p3 = pts[max(i - 1, 0)], pts[i], pts[i + 1], pts[min(i + 2, len(pts) - 1)]
        c1 = (p1[0] + (p2[0] - p0[0]) * tension / 3, p1[1] + (p2[1] - p0[1]) * tension / 3)
        c2 = (p2[0] - (p3[0] - p1[0]) * tension / 3, p2[1] - (p3[1] - p1[1]) * tension / 3)
        n = max(2, int(math.hypot(p2[0] - p1[0], p2[1] - p1[1]) / step))
        for k in range(n):
            t = k / n
            a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
            out.append((a * p1[0] + b * c1[0] + c * c2[0] + d * p2[0], a * p1[1] + b * c1[1] + c * c2[1] + d * p2[1]))
    out.append(pts[-1])
    return out


def treeline(pts, bottom, rnd, lo=5, hi=13, step=20):
    """A ridge whose edge is scalloped like a forest seen from afar."""
    s = sample(pts, step)
    d = [f"M{f(s[0][0])} {f(s[0][1])}"]
    for a, b in zip(s, s[1:]):
        bump = rnd.uniform(lo, hi)
        d.append(f"Q{f((a[0] + b[0]) / 2)} {f((a[1] + b[1]) / 2 - bump)} {f(b[0])} {f(b[1])}")
    return "".join(d) + f"L{f(s[-1][0])} {f(bottom)}L{f(s[0][0])} {f(bottom)}Z"


def sparkle(cx, cy, r):
    k = r * 0.16
    return (f"M{f(cx)} {f(cy - r)}Q{f(cx + k)} {f(cy - k)} {f(cx + r)} {f(cy)}Q{f(cx + k)} {f(cy + k)} {f(cx)} {f(cy + r)}"
            f"Q{f(cx - k)} {f(cy + k)} {f(cx - r)} {f(cy)}Q{f(cx - k)} {f(cy - k)} {f(cx)} {f(cy - r)}Z")


def cloud(cx, cy, w, h, rnd, lobes=5):
    """A flat-bottomed cloud: a row of lobes over a rounded base."""
    d = [rect(cx - w / 2, cy - h * 0.28, w, h * 0.5, h * 0.25)]
    for i in range(lobes):
        t = (i + 0.5) / lobes
        r = h * (0.30 + 0.28 * math.sin(math.pi * t)) * rnd.uniform(0.85, 1.1)
        d.append(circle(cx - w / 2 + w * (0.12 + 0.76 * t), cy - h * 0.22 - r * 0.25, r))
    return "".join(d)


def roof(cx, y, hw, h, tw, up=16, sag=12):
    """A Korean roof seen from the front: narrow ridge, concave slopes, upturned eaves. y is the eave line."""
    return (f"M{f(cx - tw)} {f(y - h)}L{f(cx + tw)} {f(y - h)}"
            f"Q{f(cx + hw * 0.52)} {f(y - h * 0.12)} {f(cx + hw)} {f(y - up)}L{f(cx + hw - 10)} {f(y + 6)}"
            f"Q{f(cx)} {f(y + sag + 8)} {f(cx - hw + 10)} {f(y + 6)}L{f(cx - hw)} {f(y - up)}"
            f"Q{f(cx - hw * 0.52)} {f(y - h * 0.12)} {f(cx - tw)} {f(y - h)}Z")


def mirror_y(d, axis):
    """The same path reflected in the horizontal line y = axis (absolute M/L/Q/C/Z paths only)."""
    def flip(m):
        nums = [float(n) for n in re.findall(r"-?\d+\.?\d*", m.group(2))]
        return m.group(1) + " ".join(f(n) if i % 2 == 0 else f(2 * axis - n) for i, n in enumerate(nums))
    assert not re.search(r"[a-zHhVvAaSsTt]", d), "mirror_y needs absolute M/L/Q/C paths"
    return re.sub(r"([MLQC])([^MLQCZ]*)", flip, d)


# ---- scene ----

class Layer:
    def __init__(self, scene, name, depth, blur, twinkle, drift, opacity):
        self.scene, self.name, self.depth, self.blur, self.twinkle, self.drift, self.opacity = scene, name, depth, blur, twinkle, drift, opacity
        self.items = []

    def path(self, d, fill="none", fo=1.0, stroke=None, sw=0.0, so=1.0, cap=None, join=None, rule=None):
        a = [f'd="{d}"', f'fill="{fill}"']
        if fo < 1:
            a.append(f'fill-opacity="{fo:.2f}"')
        if rule:
            a.append(f'fill-rule="{rule}"')
        if stroke:
            a += [f'stroke="{stroke}"', f'stroke-width="{f(sw)}"']
            if so < 1:
                a.append(f'stroke-opacity="{so:.2f}"')
            if cap:
                a.append(f'stroke-linecap="{cap}"')
            if join:
                a.append(f'stroke-linejoin="{join}"')
        self.items.append("<path " + " ".join(a) + "/>")
        return self


class Scene:
    def __init__(self, name, title_y, grain=0.035, accents=()):
        self.name, self.title_y, self.grain = name, title_y, grain
        self.allowed = {c.upper() for c in BLUES | set(accents)}
        self.defs, self.layers = [], []

    def _stops(self, stops):
        out = []
        for s in stops:
            off, color, op = (s + (1.0,))[:3]
            out.append(f'<stop offset="{off:.3f}" stop-color="{color}"' + (f' stop-opacity="{op:.2f}"' if op < 1 else "") + "/>")
        return "".join(out)

    def lin(self, x1, y1, x2, y2, stops):
        i = f"g{len(self.defs)}"
        self.defs.append(f'<linearGradient id="{i}" gradientUnits="userSpaceOnUse" x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}">{self._stops(stops)}</linearGradient>')
        return f"url(#{i})"

    def vert(self, y1, y2, stops):
        return self.lin(0, y1, 0, y2, stops)

    def rad(self, cx, cy, r, stops):
        i = f"g{len(self.defs)}"
        self.defs.append(f'<radialGradient id="{i}" gradientUnits="userSpaceOnUse" cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}">{self._stops(stops)}</radialGradient>')
        return f"url(#{i})"

    def layer(self, name, depth, blur=0, twinkle=False, drift=None, opacity=1.0):
        self.layers.append(Layer(self, name, depth, blur, twinkle, drift, opacity))
        return self.layers[-1]

    def svg(self, preview=False):
        head = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" data-title-y="{self.title_y:.2f}" data-grain="{self.grain:.3f}">')
        defs = "".join(self.defs)
        if preview:  # a browser needs a real filter to show data-blur; the app does it with a RenderEffect
            defs += "".join(f'<filter id="b{i}" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="{l.blur}"/></filter>' for i, l in enumerate(self.layers) if l.blur)
        body = []
        for i, l in enumerate(self.layers):
            a = [f'id="{l.name}"', f'data-depth="{l.depth:.2f}"']
            if l.blur:
                a.append(f'data-blur="{l.blur}"')
                if preview:
                    a.append(f'filter="url(#b{i})"')
            if l.twinkle:
                a.append('data-twinkle="1"')
            if l.drift:
                a.append(f'data-drift="{l.drift[0]},{l.drift[1]}"')
            if l.opacity < 1:
                a.append(f'opacity="{l.opacity:.2f}"')
            body.append("<g " + " ".join(a) + ">" + "".join(l.items) + "</g>")
        return head + "<defs>" + defs + "</defs>" + "".join(body) + "</svg>\n"

    def check(self):
        text = self.svg()
        colors = {c.upper() for c in re.findall(r"#[0-9A-Fa-f]{6}", text)}
        assert colors <= self.allowed, f"{self.name}: colors outside the palette: {sorted(colors - self.allowed)}"
        paths = text.count("<path ")
        assert paths <= MAX_PATHS, f"{self.name}: {paths} paths"
        assert len(self.layers) <= MAX_LAYERS, f"{self.name}: {len(self.layers)} layers"
        assert sum(1 for l in self.layers if l.blur) <= 2, f"{self.name}: more than 2 blurred layers"
        assert len(text.encode()) <= MAX_BYTES, f"{self.name}: {len(text.encode())} bytes"
        return paths, len(text.encode())


# ---- pieces shared by the scenes ----

def stars(layer, rnd, n, y_max, y_min=0, big=5):
    for _ in range(n):
        x, y = rnd.uniform(0, W), rnd.uniform(y_min, y_max)
        layer.path(circle(x, y, rnd.choice((1.4, 1.8, 2.4, 3.0))), WHITE, rnd.uniform(0.35, 0.9))
    for _ in range(big):
        layer.path(sparkle(rnd.uniform(60, W - 60), rnd.uniform(y_min + 40, y_max), rnd.uniform(9, 16)), WHITE, rnd.uniform(0.6, 0.95))


def facets(layer, scene, pts, color, strength=0.22, reach=260, step=18):
    """Moonlit side of each peak: a soft wedge falling to the left of every local summit."""
    s = sample(pts, step)
    for i in range(2, len(s) - 2):
        if s[i][1] < s[i - 1][1] and s[i][1] <= s[i + 1][1] and s[i][1] < s[i - 2][1] - 2:
            px, py = s[i]
            left = [p for p in s[:i + 1] if p[0] >= px - reach]
            if len(left) < 3:
                continue
            d = poly(left, close=False) + f"L{f(px + reach * 0.10)} {f(py + reach * 0.9)}L{f(left[0][0])} {f(left[0][1] + reach * 0.35)}Z"
            layer.path(d, scene.lin(px, py, px - reach * 0.5, py + reach * 0.8, [(0, color, strength), (1, color, 0)]))


def mist(layer, scene, y, h, color=WHITE, strength=0.12, x0=-60, x1=W + 60):
    layer.path(rect(x0, y - h / 2, x1 - x0, h), scene.vert(y - h / 2, y + h / 2, [(0, color, 0), (0.5, color, strength), (1, color, 0)]))


def canopy(layer, rnd, cx, cy, r, dark, lit, lobes=6):
    """A tree crown: a cluster of round lobes, with a few lit lobes up and to the left."""
    pts = [(cx + rnd.uniform(-r, r) * 0.8, cy + rnd.uniform(-r, r) * 0.55, r * rnd.uniform(0.45, 0.75)) for _ in range(lobes)]
    layer.path("".join(circle(*p) for p in pts) + circle(cx, cy, r * 0.7), dark)
    layer.path("".join(circle(x - rr * 0.22, y - rr * 0.25, rr * 0.55) for x, y, rr in pts[:lobes // 2]), lit, 0.55)


def vignette(layer, scene, strength=0.4):
    for cx, cy in ((0, 0), (W, 0), (0, H), (W, H)):
        layer.path(rect(0, 0, W, H), scene.rad(cx, cy, 900, [(0, DEEP, strength), (1, DEEP, 0)]))


# ---- public scenes ----

def flash1():
    """Moon, ranges in mist and a pagoda by the lake."""
    s, rnd = Scene("flash1", 0.76), random.Random(11)
    horizon, moon = 1250, (690, 640, 285)

    sky = s.layer("sky", 0.0)
    sky.path(rect(0, 0, W, H), s.vert(0, horizon, [(0, ACTION), (0.16, B3), (0.36, MID), (0.56, B4), (0.80, B5), (1, B4)]))
    glow = s.layer("glow", 0.0)
    glow.path(rect(0, 0, W, H), s.rad(moon[0], moon[1], 760, [(0, B6, 0.55), (0.45, LIGHT, 0.22), (1, LIGHT, 0)]))
    glow.path(rect(0, 0, W, 700), s.vert(0, 700, [(0, B1, 0.45), (1, B1, 0)]))

    st = s.layer("stars", 0.05, twinkle=True)
    stars(st, rnd, 46, 520, big=4)

    orb = s.layer("orb", 0.08)
    for r, o in ((430, 0.07), (360, 0.10), (318, 0.16)):
        orb.path(circle(moon[0], moon[1], r), LIGHT, o)
    orb.path(circle(*moon), s.rad(moon[0] - 90, moon[1] - 110, 420, [(0, WHITE), (0.45, B6), (1, LIGHT)]))
    for dx, dy, r in ((-120, -60, 46), (70, -130, 30), (110, 40, 58), (-40, 110, 34), (-170, 70, 20), (20, -10, 22)):
        orb.path(ellipse(moon[0] + dx, moon[1] + dy, r, r * 0.86), B5, 0.20)
        orb.path(ellipse(moon[0] + dx + r * 0.12, moon[1] + dy + r * 0.14, r * 0.8, r * 0.66), B6, 0.25)
    orb.path(circle(*moon), s.vert(moon[1] + 40, moon[1] + moon[2], [(0, B5, 0), (1, B5, 0.75)]))  # the moon sinks into the haze

    clouds = s.layer("clouds", 0.12, drift=(24, 0))
    for cx, cy, w, h, o in ((250, 420, 420, 46, 0.30), (820, 330, 300, 34, 0.24), (560, 560, 520, 30, 0.28), (930, 760, 380, 26, 0.34), (160, 700, 300, 24, 0.22)):
        clouds.path(rect(cx - w / 2, cy - h / 2, w, h, h / 2), s.lin(cx - w / 2, 0, cx + w / 2, 0, [(0, B6, 0), (0.3, B6, o), (0.7, B6, o), (1, B6, 0)]))
    for x, y, k in ((430, 500, 1.0), (470, 478, 0.8), (392, 470, 0.7), (520, 520, 0.6)):  # birds crossing the moon
        clouds.path(f"M{f(x - 16 * k)} {f(y - 5 * k)}Q{f(x - 6 * k)} {f(y - 9 * k)} {f(x)} {f(y)}Q{f(x + 6 * k)} {f(y - 9 * k)} {f(x + 16 * k)} {f(y - 5 * k)}",
                    stroke=B2, sw=3 * k, so=0.7, cap="round", join="round")

    r1 = [(-40, 900), (90, 840), (210, 770), (300, 800), (420, 868), (560, 900), (700, 842), (800, 792), (900, 838), (1010, 880), (1120, 850)]
    r2 = [(-40, 990), (60, 960), (170, 905), (290, 940), (380, 985), (500, 930), (590, 880), (700, 930), (820, 990), (960, 950), (1120, 905)]
    far = s.layer("far", 0.20)
    far.path(ridge(r1, horizon), s.vert(760, 1150, [(0, B5), (0.6, B4), (1, B5)]))
    facets(far, s, r1, B6, 0.30)
    far.path(smooth(r1), stroke=B6, sw=2.5, so=0.5)
    far.path(ridge(r2, horizon), s.vert(880, 1200, [(0, B4), (0.55, MID), (1, B4)]))
    facets(far, s, r2, B5, 0.28)
    far.path(smooth(r2), stroke=B5, sw=2.5, so=0.45)

    m1 = s.layer("mist1", 0.25, drift=(-30, 0))
    mist(m1, s, 1000, 150, B6, 0.30)
    mist(m1, s, 900, 90, WHITE, 0.14)

    r3 = [(-40, 1090), (80, 1040), (200, 985), (310, 1030), (430, 1090), (540, 1060), (650, 1000), (760, 1050), (880, 1110), (1000, 1070), (1120, 1020)]
    r4 = [(-40, 1180), (70, 1150), (190, 1090), (300, 1060), (400, 1110), (520, 1170), (640, 1150), (740, 1110), (840, 1150), (960, 1190), (1120, 1160)]
    mid = s.layer("mid", 0.40)
    mid.path(ridge(r3, horizon), s.vert(980, 1250, [(0, B3), (0.6, MID), (1, B4)]))
    facets(mid, s, r3, B4, 0.30)
    mid.path(smooth(r3), stroke=B5, sw=3, so=0.45)
    mid.path(treeline(r4, horizon + 10, rnd, 4, 10, 16), s.vert(1060, 1260, [(0, ACTION), (0.7, B3), (1, MID)]))
    facets(mid, s, r4, B3, 0.34, reach=220)

    m2 = s.layer("mist2", 0.45, drift=(36, 0))
    mist(m2, s, 1200, 130, B6, 0.34)
    mist(m2, s, 1105, 70, WHITE, 0.10)

    # ---- water ----
    water = s.layer("water", 0.75)
    water.path(rect(0, horizon, W, H - horizon), s.vert(horizon, 2100, [(0, B4), (0.07, B3), (0.30, ACTION), (0.62, B1), (1, NIGHT)]))
    water.path(rect(0, horizon - 6, W, 30), s.vert(horizon - 6, horizon + 24, [(0, B6, 0), (0.4, B6, 0.55), (1, B6, 0)]))  # bright line where water meets mist
    for i in range(46):  # the moon on the water: broken streaks that widen and fade towards us
        t = i / 45
        y = horizon + 26 + t ** 1.25 * 560
        w = (60 + 300 * t) * rnd.uniform(0.45, 1.0)
        x = moon[0] - 20 + rnd.uniform(-1, 1) * (20 + 90 * t)
        h = 5 + 7 * t
        water.path(rect(x - w / 2, y, w, h, h / 2), B6, 0.62 * (1 - t) ** 1.3 + 0.05)
    for i in range(34):  # long ripples
        y = horizon + 40 + rnd.random() ** 0.8 * 900
        w = rnd.uniform(120, 420)
        x = rnd.uniform(-60, W)
        water.path(rect(x, y, w, 3.5, 1.7), B5 if y < 1700 else B3, rnd.uniform(0.08, 0.2))
    far_shore = [(-40, 1262), (60, 1244), (150, 1236), (240, 1246), (330, 1232), (430, 1250), (520, 1262)]
    water.path(treeline(far_shore, horizon + 16, rnd, 5, 11, 15), s.vert(1225, 1270, [(0, B2), (1, ACTION)]))
    water.path(mirror_y(ridge(far_shore, horizon + 8), horizon + 8), B2, 0.28)

    # ---- the pagoda on its islet (light comes from the upper left) ----
    sub = s.layer("subject", 0.55)
    cx, ground = 870, 1296
    islet = [(560, ground + 14), (640, ground - 10), (720, ground - 26), (800, ground - 30), (960, ground - 30), (1040, ground - 18), (1120, ground - 4)]
    refl = s.layer("reflection", 0.75, opacity=0.5)

    def tier(y_eave, hw, h, tw, body_hw, body_h, cols):
        top = y_eave - h
        body_top = y_eave + 4
        d_body = rect(cx - body_hw, body_top, body_hw * 2, body_h)
        sub.path(d_body, B1)
        sub.path(rect(cx - body_hw, body_top, body_hw * 0.8, body_h), B2)  # lit half
        gap = body_hw * 2 / cols
        for c in range(cols):  # glowing paper panels between dark columns
            x = cx - body_hw + gap * c + gap * 0.16
            sub.path(rect(x, body_top + body_h * 0.22, gap * 0.68, body_h * 0.56, 3), s.vert(body_top, body_top + body_h, [(0, B5), (1, B4)]), 0.92)
            sub.path(rect(x + gap * 0.31, body_top + body_h * 0.22, gap * 0.06, body_h * 0.56), B1, 0.8)
            sub.path(rect(x, body_top + body_h * 0.47, gap * 0.68, body_h * 0.05), B1, 0.8)
        sub.path(rect(cx - body_hw - 14, body_top + body_h - 12, body_hw * 2 + 28, 5, 2), B1)  # balcony rail
        for c in range(cols * 2 + 1):
            sub.path(rect(cx - body_hw - 12 + (body_hw * 2 + 24) * c / (cols * 2) - 1.5, body_top + body_h - 12, 3, 14), B1)
        d_roof = roof(cx, y_eave, hw, h, tw)
        sub.path(d_roof, s.vert(top, y_eave + 20, [(0, B2), (0.7, B1), (1, NIGHT)]))
        for k in range(1, 12):  # tile lines fanning from the ridge to the eave
            t = k / 12
            x_top, x_bot = cx - tw + 2 * tw * t, cx - hw * 0.94 + 2 * hw * 0.94 * t
            y_bot = y_eave - 4 - 16 * abs(2 * t - 1) ** 2.2 + 2
            sub.path(f"M{f(x_top)} {f(top + 3)}Q{f((x_top + x_bot) / 2)} {f((top + y_bot) / 2 + 12)} {f(x_bot)} {f(y_bot)}", stroke=B3, sw=1.6, so=0.38)
        sub.path(f"M{f(cx - tw)} {f(top)}Q{f(cx - hw * 0.52)} {f(y_eave - h * 0.12)} {f(cx - hw)} {f(y_eave - 16)}", stroke=B5, sw=3.5, so=0.85, cap="round")  # rim light
        sub.path(f"M{f(cx - tw)} {f(top + 1.5)}L{f(cx + tw)} {f(top + 1.5)}", stroke=B4, sw=3, so=0.7, cap="round")
        sub.path(f"M{f(cx - hw + 12)} {f(y_eave + 7)}Q{f(cx)} {f(y_eave + 21)} {f(cx + hw - 12)} {f(y_eave + 7)}", stroke=NIGHT, sw=5, so=0.55)  # shadow under the eave
        for d in (poly([(cx - body_hw, body_top), (cx + body_hw, body_top), (cx + body_hw, body_top + body_h), (cx - body_hw, body_top + body_h)]), d_roof):
            refl.path(mirror_y(d, ground + 6), B1)

    sub.path(treeline(islet, ground + 30, rnd, 6, 14, 16), s.vert(ground - 40, ground + 20, [(0, B2), (1, B1)]))
    for x, y, r in ((640, ground - 40, 44), (700, ground - 62, 56), (1020, ground - 58, 62), (1075, ground - 34, 48), (590, ground - 16, 34)):
        canopy(sub, rnd, x, y, r, B1, B3)
    sub.path(rect(cx - 150, ground - 52, 300, 26, 4), B1)  # stone base
    sub.path(rect(cx - 150, ground - 52, 300, 5, 2), B3, 0.6)
    sub.path(rect(cx - 130, ground - 74, 260, 24, 3), B1)
    sub.path(rect(cx - 130, ground - 74, 260, 4, 2), B3, 0.5)
    tier(ground - 162, 196, 60, 130, 112, 84, 5)
    tier(ground - 306, 158, 54, 100, 88, 78, 4)
    tier(ground - 440, 124, 78, 8, 64, 74, 3)
    top = ground - 440 - 78
    sub.path(rect(cx - 4, top - 96, 8, 100, 4), B1)  # finial
    for i, r in enumerate((15, 12, 9, 6)):
        sub.path(ellipse(cx, top - 14 - i * 20, r, 6), B1)
        sub.path(ellipse(cx - r * 0.3, top - 16 - i * 20, r * 0.5, 2.5), B4, 0.7)
    sub.path(circle(cx, top - 100, 7), B2)
    refl.path(mirror_y(treeline(islet, ground + 6, rnd, 6, 14, 16), ground + 6), B1)
    for i in range(26):  # the water breaks the reflection into bands
        y = ground + 26 + i * 17 + rnd.uniform(-3, 3)
        water_gap = rnd.uniform(160, 520)
        refl.path(rect(rnd.uniform(560, 1080 - water_gap * 0.5), y, water_gap, rnd.uniform(3, 7), 3), B3 if i < 9 else ACTION, 0.9)

    # ---- foreground: reeds and a pine bough, out of focus ----
    near = s.layer("near", 0.90)
    shore = [(-40, 1560), (60, 1520), (170, 1500), (260, 1530), (330, 1580), (380, 1640)]
    near.path(treeline(shore, H, rnd, 6, 16, 18), s.vert(1490, 1760, [(0, B1), (1, NIGHT)]))
    for bx, by, hgt in ((70, 1520, 250), (150, 1500, 330), (235, 1516, 210)):  # three pines on the near shore
        near.path(rect(bx - 5, by - hgt, 10, hgt + 10, 4), DEEP)
        for k in range(5):
            y = by - hgt + 16 + k * hgt * 0.15
            w = 46 + k * 22
            near.path(f"M{f(bx - w)} {f(y + 20)}Q{f(bx - w * 0.5)} {f(y - 4)} {f(bx)} {f(y - 16)}Q{f(bx + w * 0.5)} {f(y - 4)} {f(bx + w)} {f(y + 20)}Q{f(bx)} {f(y + 4)} {f(bx - w)} {f(y + 20)}Z", DEEP if k % 2 else B1)
            near.path(f"M{f(bx - w * 0.9)} {f(y + 15)}Q{f(bx - w * 0.5)} {f(y - 3)} {f(bx - 4)} {f(y - 14)}", stroke=B3, sw=2.5, so=0.5, cap="round")

    bloom = s.layer("bloom", 1.0, blur=7, drift=(-14, 10))
    for i in range(22):  # reeds leaning in from the bottom corners
        left = i % 2 == 0
        x = rnd.uniform(-20, 260) if left else rnd.uniform(820, 1100)
        base, tip = rnd.uniform(1820, 2000), rnd.uniform(1420, 1640)
        lean = rnd.uniform(20, 130) * (1 if left else -1)
        bloom.path(f"M{f(x)} {f(base)}Q{f(x + lean * 0.2)} {f((base + tip) / 2)} {f(x + lean)} {f(tip)}", stroke=DEEP, sw=rnd.uniform(7, 13), so=0.9, cap="round")
        if i % 3 == 0:
            bloom.path(ellipse(x + lean, tip + 14, 9, 30), DEEP, 0.9)

    motes = s.layer("motes", 0.9, drift=(10, -40), twinkle=True)
    for _ in range(22):
        x, y = rnd.uniform(40, W - 40), rnd.uniform(1180, 1760)
        r = rnd.uniform(1.5, 3.5)
        motes.path(circle(x, y, r), B6, rnd.uniform(0.35, 0.75))

    shade = s.layer("shade", 0.0)
    shade.path(rect(0, 1480, W, H - 1480), s.vert(1480, 2060, [(0, NIGHT, 0), (0.55, NIGHT, 0.82), (1, NIGHT, 1)]))
    vignette(shade, s, 0.34)
    return s


PUBLIC = [flash1]


def write(scenes, preview):
    OUT.mkdir(parents=True, exist_ok=True)
    for make in scenes:
        s = make()
        paths, size = s.check()
        (OUT / f"{s.name}.svg").write_text(s.svg())
        if preview:
            p = ROOT / "build/splash-preview"
            p.mkdir(parents=True, exist_ok=True)
            (p / f"{s.name}.svg").write_text(s.svg(preview=True))
        print(f"{s.name}: {len(s.layers)} layers, {paths} paths, {size // 1024} KB")


if __name__ == "__main__":
    write(PUBLIC, "--preview" in sys.argv)
