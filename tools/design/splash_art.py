"""One-off: picsIcons -> splash webps + launcher icon. Usage: art.py <picsIcons> <outdir>"""
import sys, os, glob
import numpy as np
from PIL import Image, ImageFilter, ImageOps

SRC, OUT = sys.argv[1], sys.argv[2]
W, H = 1080, 2400
os.makedirs(OUT, exist_ok=True)

def hsv(a):  # a: float rgb 0..1 -> h (deg), s, v
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(-1), a.min(-1)
    d = mx - mn + 1e-9
    h = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4)) * 60
    return h, d / (mx + 1e-9), mx

def rgb(h, s, v):
    h = (h % 360) / 60
    c = v * s
    x = c * (1 - np.abs(h % 2 - 1))
    z = np.zeros_like(h)
    i = np.floor(h).astype(int) % 6
    r = np.choose(i, [c, x, z, z, x, c]); g = np.choose(i, [x, c, c, x, z, z]); b = np.choose(i, [z, z, x, c, c, x])
    return np.stack([r, g, b], -1) + (v - c)[..., None]

def smooth(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)

def to_brand_blue(im):
    """Blues move to the hue of #0047A0 (213 deg); the #0F64CD brightness (V .80) drops to #0047A0's (V .63)."""
    a = np.asarray(im, float) / 255
    h, s, v = hsv(a)
    w = smooth(h, 190, 204) * (1 - smooth(h, 238, 256)) * smooth(s, 0.25, 0.6)  # only saturated blues; purples/pinks/reds stay
    h2 = h + (213.4 - h) * w
    curve = np.interp(v, [0, 0.5, 0.8, 1.0], [0, 0.5, 0.63, 0.86])
    v2 = v + (curve - v) * w
    s2 = np.clip(s * (1 + 0.07 * w), 0, 1)
    return Image.fromarray((rgb(h2, s2, v2).clip(0, 1) * 255).astype(np.uint8))

def erase(im, x0, y0, x1, y1):
    """Paint out baked text: each row is a blend of the art just left and right of the box."""
    a = np.asarray(im, float).copy()
    l = a[y0:y1, x0 - 12:x0].mean(1, keepdims=True); r = a[y0:y1, x1:x1 + 12].mean(1, keepdims=True)
    t = np.linspace(0, 1, x1 - x0)[None, :, None]
    a[y0:y1, x0:x1] = l * (1 - t) + r * t
    out = Image.fromarray(a.astype(np.uint8))
    box = (x0 - 16, y0 - 16, x1 + 16, y1 + 16)
    out.paste(out.crop(box).filter(ImageFilter.GaussianBlur(6)), box)
    return out

FLASH = {
    'flash1': [(190, 1650, 1010, 2150), (430, 2335, 770, 2385)],
    'flash2': [(100, 1150, 980, 1440), (160, 1515, 920, 1625), (420, 2315, 680, 2385)],
    'flash3': [(110, 1155, 970, 1435), (160, 1500, 920, 1605), (420, 2315, 680, 2385)],
}
for name, boxes in FLASH.items():
    im = Image.open(f'{SRC}/{name}.png').convert('RGB')
    if name == 'flash1':  # drop the mock phone frame and status bar: the sky row under them fills the top
        a = np.asarray(im).copy(); a[:245] = a[245:246]; im = Image.fromarray(a)
    for b in boxes: im = erase(im, *b)
    im = to_brand_blue(im).filter(ImageFilter.UnsharpMask(2, 60, 2))
    im.save(f'{OUT}/splash_{name}.webp', quality=88, method=6)

# The 11 photo screens are drawn from scratch by tools/design/splash_scenes.py.

# Launcher icon: art scaled so the launcher mask (72 of 108dp) crops the baked rounded border.
ic = Image.open(f'{SRC}/icono.png').convert('RGB')
side = 432; art = round(side * 80 / 108)
fg = Image.new('RGB', (side, side), (238, 245, 253))
fg.paste(ic.resize((art, art), Image.LANCZOS).filter(ImageFilter.UnsharpMask(1, 40, 2)), ((side - art) // 2,) * 2)
fg.save(f'{OUT}/ic_launcher_art.webp', lossless=True)
