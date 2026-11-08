"""Title banner of the Hands-On Delight page: a recorded kitchen shot (out of focus and darkened behind the title, the pot
sharp on the right), the name in Fraunces (OFL, fonts/), the fire and lanterns live in a seamless loop.

Usage (from art/page): python banner.py <frames dir with f0000.png...> <out base> [top 0..1]
Writes <out base>.png (still, 1600x520) and <out base>.gif (animated, 1000 wide).
"""
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1600, 520
GIF_W = 1000
FPS = 25
LOOP, FADE = 75, 12


def scene_masks():
    x = np.linspace(0, 1, W)[None, :, None]
    y = np.linspace(0, 1, H)[:, None, None]
    focus = np.clip((x - 0.45) / 0.3, 0, 1)
    shade = np.clip(1.0 - 0.8 * np.clip((0.78 - x) / 0.6, 0, 1) ** 1.1, 0.2, 1.0)
    shade = shade * (1.0 - 0.3 * np.clip((y - 0.75) / 0.25, 0, 1))
    return focus, shade


def scene(src, top, masks):
    src = src.resize((W, round(src.height * W / src.width)), Image.LANCZOS)
    y0 = max(0, min(src.height - H, int(src.height * top)))
    crop = src.crop((0, y0, W, y0 + H))
    sharp = np.array(crop, np.float32)
    soft = np.array(crop.filter(ImageFilter.GaussianBlur(7)), np.float32)
    focus, shade = masks
    bg = soft * (1 - focus) + sharp * focus
    tint = np.array([34, 18, 10], np.float32)  # warm kitchen brown, not black
    bg = bg * shade + tint * (1 - shade)
    return Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def title_layer():
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    font = ImageFont.truetype("fonts/Fraunces.ttf", 108)
    font.set_variation_by_axes([144, 700, 100, 1])
    small = ImageFont.truetype("fonts/Fraunces.ttf", 29)
    small.set_variation_by_axes([24, 500, 50, 0])
    tx, ty = 70, 140
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).text((tx, ty), "Hands-On Delight", font=font, fill=255)
    glow = Image.new("RGBA", (W, H), (255, 160, 70, 0))
    glow.putalpha(mask.filter(ImageFilter.GaussianBlur(22)).point(lambda a: int(a * 0.45)))
    layer.alpha_composite(glow)
    shadow = Image.new("RGBA", (W, H), (20, 8, 0, 0))
    shadow.putalpha(mask.filter(ImageFilter.GaussianBlur(3)).point(lambda a: int(a * 0.85)))
    layer.alpha_composite(shadow, (4, 6))
    grad = np.zeros((H, W, 4), np.uint8)
    gy = np.clip((np.arange(H)[:, None] - ty - 25) / 140.0, 0, 1)
    top_c, bot_c = np.array([255, 238, 196]), np.array([228, 120, 52])
    grad[..., :3] = (top_c * (1 - gy[..., None]) + bot_c * gy[..., None]).astype(np.uint8)
    grad[..., 3] = np.array(mask)
    layer.alpha_composite(Image.fromarray(grad, "RGBA"))
    d = ImageDraw.Draw(layer)
    text = "COOKING POT  ·  CUTTING BOARD  ·  SKILLET"
    line_y = ty + 160
    d.line([(tx + 6, line_y), (tx + 6 + d.textlength(text, font=small), line_y)], fill=(226, 140, 66, 210), width=2)
    d.text((tx + 6, line_y + 20), text, font=small, fill=(255, 226, 180, 255))
    d.text((tx + 6, line_y + 64), "An addon for Farmer's Delight", font=small, fill=(232, 196, 150, 220))
    return layer


if __name__ == "__main__":
    frames_dir, out_base = pathlib.Path(sys.argv[1]), sys.argv[2]
    top = float(sys.argv[3]) if len(sys.argv) > 3 else 0.3
    files = sorted(frames_dir.glob("f*.png"))
    src = [Image.open(f).convert("RGB") for f in files]
    masks, title = scene_masks(), title_layer()
    start = len(src) - LOOP - FADE - 5
    frames = []
    for k in range(LOOP):
        a = src[start + k]
        if k >= LOOP - FADE:  # cross-fade the end into the frames before the start: a seamless loop
            j = k - (LOOP - FADE)
            a = Image.blend(a, src[start - FADE + j], (j + 1) / (FADE + 1))
        img = scene(a, top, masks)
        img.alpha_composite(title)
        frames.append(img.convert("RGB"))
    frames[0].save(out_base + ".png")
    tmp = tempfile.mkdtemp()
    gif_h = round(H * GIF_W / W)
    for k, f in enumerate(frames):
        f.resize((GIF_W, gif_h), Image.LANCZOS).save(os.path.join(tmp, "%04d.png" % k))
    import imageio_ffmpeg
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-framerate", str(FPS), "-i",
                    os.path.join(tmp, "%04d.png"), "-vf",
                    "split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle",
                    "-loop", "0", out_base + ".gif"], check=True)
    shutil.rmtree(tmp)
    print(out_base, len(frames), "frames", round(os.path.getsize(out_base + ".gif") / 1e6, 2), "MB")
