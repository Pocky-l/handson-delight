"""Thin white support strip for the bottom of the CurseForge pages: a cup of coffee and one line in Fraunces (OFL,
fonts/). Links to Ko-fi on the page. The file name avoids "banner": ad blockers hide such images.

The cup is the Hot Cocoa item of Farmer's Delight (MIT), taken from its jar
(assets/farmersdelight/textures/item/hot_cocoa.png) and not kept in the repo. Item textures are scaled by whole pixels.

Usage (from art/page): python coffee.py <icon.png> <out.png>
Writes a 1668x120 PNG, shown at half size (834x60) on the page.
"""
import sys

from PIL import Image, ImageDraw, ImageFont

W, H = 1668, 120
ICON = 100
INK = (59, 35, 20)
ACCENT = (192, 96, 42)


def font(size, weight):
    f = ImageFont.truetype("fonts/Fraunces.ttf", size)
    f.set_variation_by_axes([72, weight, 50, 0])
    return f


def main(icon_path, out):
    img = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    d = ImageDraw.Draw(img)
    parts = [("Enjoying my mods?  ", font(46, 500), INK), ("Buy me a coffee", font(46, 700), ACCENT),
             (" on Ko-fi", font(46, 500), INK)]
    icon = Image.open(icon_path).convert("RGBA")
    icon = icon.crop(icon.getbbox())
    if max(icon.size) <= 32:  # an item texture: whole pixels, no smoothing
        s = ICON // max(icon.size)
        icon = icon.resize((icon.width * s, icon.height * s), Image.NEAREST)
    else:
        icon = icon.resize((ICON, ICON), Image.LANCZOS)
    gap = 24
    widths = [d.textlength(t, font=f) for t, f, _ in parts]
    total = icon.width + gap + sum(widths)
    x = (W - total) / 2
    img.alpha_composite(icon, (round(x), (H - icon.height) // 2))
    x += icon.width + gap
    for (t, f, c), w in zip(parts, widths):
        d.text((x, H / 2), t, font=f, fill=c, anchor="lm")
        x += w
    img.convert("RGB").save(out, optimize=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
