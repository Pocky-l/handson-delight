"""Support strip for the bottom of the CurseForge pages: a pastel cream card with rounded corners, a soft warm glow
behind the cup, a few faint coffee beans at the ends, and one line in Fraunces (OFL, fonts/). Links to Ko-fi on the page.
The file name avoids "banner": ad blockers hide such images.

The cup is the Hot Cocoa item of Farmer's Delight (MIT), taken from its jar
(assets/farmersdelight/textures/item/hot_cocoa.png) and not kept in the repo. Item textures are scaled by whole pixels.

Usage (from art/page): python coffee.py <icon.png> <out.png>
Writes a 1668x120 PNG with transparent corners, shown at half size (834x60) on the page.
"""
import math
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1668, 120
ICON = 100
RADIUS = 26
LEFT, RIGHT = (255, 246, 232), (252, 233, 222)  # cream to a peach blush
EDGE = (236, 212, 190)
GLOW = (255, 214, 170)
BEAN = (150, 96, 60)
INK = (74, 46, 28)
ACCENT = (196, 104, 56)


def font(size, weight):
    f = ImageFont.truetype("fonts/Fraunces.ttf", size)
    f.set_variation_by_axes([72, weight, 50, 0])
    return f


def background():
    t = np.linspace(0, 1, W)[None, :, None]
    y = np.linspace(0, 1, H)[:, None, None]
    left, right = np.array(LEFT, np.float32), np.array(RIGHT, np.float32)
    rgb = left * (1 - t) + right * t
    rgb = rgb * (1.0 - 0.025 * y)  # a touch darker at the bottom, like paper in light from above
    rgb = np.broadcast_to(rgb, (H, W, 3))
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def glow(cx, cy):
    layer = Image.new("RGBA", (W, H), GLOW + (0,))
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).ellipse((cx - 120, cy - 70, cx + 120, cy + 70), fill=105)
    layer.putalpha(mask.filter(ImageFilter.GaussianBlur(34)))
    return layer


def bean(size, angle):
    s = size * 4
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    w, h = s * 0.62, s * 0.88
    box = ((s - w) / 2, (s - h) / 2, (s + w) / 2, (s + h) / 2)
    d.ellipse(box, fill=BEAN + (255,))
    pts = [(s / 2 + math.sin(k / 10 * math.pi) * w * 0.12, (s - h) / 2 + h * (0.12 + 0.76 * k / 10)) for k in range(11)]
    d.line(pts, fill=(0, 0, 0, 0), width=max(2, s // 14))
    img = img.rotate(angle, resample=Image.BICUBIC, expand=True)
    return img.resize((img.width // 4, img.height // 4), Image.LANCZOS)


def beans(card, text_left, text_right):
    rnd = random.Random(7)
    placed = []
    for _ in range(400):
        if len(placed) >= 14:
            break
        x = rnd.uniform(30, W - 30)
        if text_left - 60 < x < text_right + 60:
            continue
        y = rnd.uniform(18, H - 18)
        if any((x - px) ** 2 + (y - py) ** 2 < 70 ** 2 for px, py in placed):
            continue
        placed.append((x, y))
        b = bean(rnd.randint(18, 26), rnd.uniform(0, 360))
        a = np.array(b)
        a[..., 3] = (a[..., 3] * rnd.uniform(0.10, 0.16)).astype(np.uint8)
        b = Image.fromarray(a, "RGBA")
        card.alpha_composite(b, (round(x - b.width / 2), round(y - b.height / 2)))


def cup(icon_path):
    icon = Image.open(icon_path).convert("RGBA")
    icon = icon.crop(icon.getbbox())
    if max(icon.size) <= 32:
        s = ICON // max(icon.size)
        return icon.resize((icon.width * s, icon.height * s), Image.NEAREST)
    return icon.resize((ICON, ICON), Image.LANCZOS)


def main(icon_path, out):
    card = background()
    d = ImageDraw.Draw(card)
    parts = [("Enjoying my mods?  ", font(46, 500), INK), ("Buy me a coffee", font(46, 700), ACCENT),
             (" on Ko-fi", font(46, 500), INK)]
    icon = cup(icon_path)
    gap = 26
    widths = [d.textlength(t, font=f) for t, f, _ in parts]
    total = icon.width + gap + sum(widths)
    x0 = (W - total) / 2
    ix, iy = round(x0), (H - icon.height) // 2 - 2
    beans(card, x0, x0 + total)
    card.alpha_composite(glow(ix + icon.width // 2, H // 2))
    # a soft shadow under the cup, as if it stands on the card
    shadow = Image.new("RGBA", (W, H), (90, 50, 25, 0))
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).ellipse((ix + 14, iy + icon.height - 12, ix + icon.width - 14, iy + icon.height + 6), fill=110)
    shadow.putalpha(m.filter(ImageFilter.GaussianBlur(6)))
    card.alpha_composite(shadow)
    card.alpha_composite(icon, (ix, iy))
    d = ImageDraw.Draw(card)
    x = x0 + icon.width + gap
    for (t, f, c), w in zip(parts, widths):
        d.text((x, H / 2), t, font=f, fill=c, anchor="lm")
        x += w
    # thin edge and rounded corners
    ImageDraw.Draw(card).rounded_rectangle((1, 1, W - 2, H - 2), RADIUS, outline=EDGE, width=3)
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, W - 1, H - 1), RADIUS, fill=255)
    card.putalpha(mask)
    card.save(out, optimize=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
