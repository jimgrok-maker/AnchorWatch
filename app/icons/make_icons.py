#!/usr/bin/env python3
"""Paint a glossy white-on-blue admiralty anchor launcher icon.

Writes:
  res/mipmap-*/ic_launcher.png          (My Files / APK thumbnail)
  res/mipmap-*/ic_launcher_round.png
  res/drawable/ic_launcher_foreground.png
and removes leftover vector foreground XML so the PNG wins.
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

NAVY = (8, 54, 140, 255)
WHITE = (255, 255, 255, 255)


def rounded_rect_mask(size: int, radius: int) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return m


def ImageChops_multiply_alpha(im: Image.Image, mask: Image.Image) -> Image.Image:
    from PIL import ImageChops
    r, g, b, a = im.split()
    a = ImageChops.multiply(a, mask)
    return Image.merge("RGBA", (r, g, b, a))


def _thicken(pts, radius: float):
    left, right = [], []
    n = len(pts)
    for i, (x, y) in enumerate(pts):
        if i == 0:
            dx, dy = pts[1][0] - x, pts[1][1] - y
        elif i == n - 1:
            dx, dy = x - pts[i - 1][0], y - pts[i - 1][1]
        else:
            dx, dy = pts[i + 1][0] - pts[i - 1][0], pts[i + 1][1] - pts[i - 1][1]
        length = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / length, dx / length
        left.append((x + nx * radius, y + ny * radius))
        right.append((x - nx * radius, y - ny * radius))
    return left + list(reversed(right))


def paint(size: int = 1024) -> Image.Image:
    radius = int(size * 0.22)
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(bg).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=NAVY)

    sheen = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(sheen)
    for y in range(size):
        t = y / (size - 1)
        if t < 0.45:
            a = int(70 * (1 - t / 0.45))
            c = (90, 160, 230, a)
        else:
            a = int(40 * ((t - 0.45) / 0.55))
            c = (0, 10, 40, a)
        sdraw.line([(0, y), (size, y)], fill=c)
    mask = rounded_rect_mask(size, radius)
    sheen = ImageChops_multiply_alpha(sheen, mask)
    bg = Image.alpha_composite(bg, sheen)

    rim = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(rim).rounded_rectangle(
        (int(size * 0.02), int(size * 0.02), size - int(size * 0.02) - 1, size - int(size * 0.02) - 1),
        radius=int(radius * 0.86),
        outline=(180, 220, 255, 50),
        width=max(2, size // 180),
    )
    bg = Image.alpha_composite(bg, rim)

    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = size / 2, size / 2 + size * 0.02

    def xy(nx: float, ny: float):
        return cx + nx * size, cy + ny * size

    ring_r = size * 0.105
    ring_w = size * 0.055
    ring_c = xy(0, -0.30)
    bbox = [
        ring_c[0] - ring_r - ring_w / 2,
        ring_c[1] - ring_r - ring_w / 2,
        ring_c[0] + ring_r + ring_w / 2,
        ring_c[1] + ring_r + ring_w / 2,
    ]
    d.ellipse(bbox, outline=WHITE, width=int(ring_w))

    stock_w = size * 0.34
    stock_h = size * 0.072
    stock_y = ring_c[1] + ring_r + size * 0.012
    d.rounded_rectangle(
        [cx - stock_w / 2, stock_y, cx + stock_w / 2, stock_y + stock_h],
        radius=stock_h / 2,
        fill=WHITE,
    )

    shank_w = size * 0.078
    shank_top = stock_y + stock_h * 0.35
    shank_bot = cy + size * 0.20
    d.rectangle([cx - shank_w / 2, shank_top, cx + shank_w / 2, shank_bot], fill=WHITE)

    crown_r = size * 0.055
    d.ellipse(
        [cx - crown_r, shank_bot - crown_r * 0.3, cx + crown_r, shank_bot + crown_r * 1.5],
        fill=WHITE,
    )

    arm_w = size * 0.072
    for sign in (-1, 1):
        pts = []
        steps = 28
        for i in range(steps + 1):
            t = i / steps
            ax = cx + sign * size * (0.02 + 0.28 * t)
            ay = shank_bot + size * (0.04 - 0.02 * t - 0.20 * t * t)
            pts.append((ax, ay))
        d.polygon(_thicken(pts, arm_w / 2), fill=WHITE)

        tip = pts[-1]
        ang = math.atan2(-0.42, sign * 0.62)
        fluke_len = size * 0.175
        fluke_w = size * 0.145
        nx, ny = math.cos(ang), math.sin(ang)
        px, py = -ny, nx
        tip2 = (tip[0] + nx * fluke_len, tip[1] + ny * fluke_len)
        left = (tip[0] + px * fluke_w * 0.42 - nx * fluke_len * 0.08, tip[1] + py * fluke_w * 0.42)
        right = (tip[0] - px * fluke_w * 0.62 - nx * fluke_len * 0.02, tip[1] - py * fluke_w * 0.62)
        d.polygon([left, tip2, right], fill=WHITE)

    hi = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(hi).ellipse(
        [cx - shank_w * 0.15, shank_top + size * 0.02, cx + shank_w * 0.12, shank_bot - size * 0.02],
        fill=(255, 255, 255, 40),
    )

    alpha = layer.split()[-1]
    shadow = Image.new("RGBA", (size, size), (0, 18, 40, 0))
    shadow.putalpha(alpha.point(lambda a: int(a * 0.35)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=size * 0.018))
    offset = (int(size * 0.012), int(size * 0.018))
    composed = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    composed.paste(shadow, offset, shadow)
    composed = Image.alpha_composite(composed, layer)
    composed = Image.alpha_composite(composed, hi)

    out = Image.alpha_composite(bg, composed)
    out.putalpha(mask)
    return out


def make_round(src: Image.Image) -> Image.Image:
    size = src.size[0]
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out.paste(src, (0, 0))
    out.putalpha(mask)
    return out


def main() -> None:
    res = Path(__file__).resolve().parents[1] / "src" / "main" / "res"
    src = paint(1024)
    rnd = make_round(src)
    print(f"painted glossy icon {src.size[0]}x{src.size[1]}")

    drawable = res / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    src.resize((432, 432), Image.Resampling.LANCZOS).save(
        drawable / "ic_launcher_foreground.png", optimize=True
    )
    xml_fg = drawable / "ic_launcher_foreground.xml"
    if xml_fg.exists():
        xml_fg.unlink()
        print("removed vector ic_launcher_foreground.xml")

    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, px in sizes.items():
        folder = res / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        src.resize((px, px), Image.Resampling.LANCZOS).save(folder / "ic_launcher.png")
        rnd.resize((px, px), Image.Resampling.LANCZOS).save(folder / "ic_launcher_round.png")
    print("wrote glossy launcher mipmaps")


if __name__ == "__main__":
    main()
