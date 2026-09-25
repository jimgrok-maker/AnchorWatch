#!/usr/bin/env python3
"""Paint a white admiralty anchor on blue and write launcher mipmaps."""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

BLUE = (1, 86, 176, 255)
BLUE_HOLE = (1, 86, 176, 255)
WHITE = (255, 255, 255, 255)
BLUE_DARK = (0, 48, 128, 255)
BLUE_LIGHT = (20, 130, 220, 255)


def _ellipse(draw: ImageDraw.ImageDraw, cx, cy, r, fill):
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill)


def _round_rect(draw: ImageDraw.ImageDraw, x0, y0, x1, y1, fill):
    r = max(1, int((y1 - y0) / 2))
    draw.rounded_rectangle((x0, y0, x1, y1), radius=r, fill=fill)


def paint_anchor(draw: ImageDraw.ImageDraw, cx: float, cy: float, h: float, hole) -> None:
    ring_cy = cy - 0.38 * h
    _ellipse(draw, cx, ring_cy, 0.125 * h, WHITE)
    _ellipse(draw, cx, ring_cy, 0.068 * h, hole)

    shank_w = 0.072 * h
    shank_top = ring_cy + 0.04 * h
    shank_bot = cy + 0.36 * h
    draw.rectangle((cx - shank_w / 2, shank_top, cx + shank_w / 2, shank_bot), fill=WHITE)

    stock_w = 0.42 * h
    stock_h = 0.07 * h
    stock_cy = cy - 0.20 * h
    _round_rect(
        draw,
        cx - stock_w / 2,
        stock_cy - stock_h / 2,
        cx + stock_w / 2,
        stock_cy + stock_h / 2,
        WHITE,
    )

    arm_cy = cy + 0.02 * h
    outer_r = 0.34 * h
    inner_r = 0.235 * h

    def pt(deg, r):
        rad = math.radians(deg)
        return (cx + r * math.sin(rad), arm_cy + r * math.cos(rad))

    outer = [pt(-82 + i * (164 / 64), outer_r) for i in range(65)]
    inner = [pt(82 - i * (164 / 64), inner_r) for i in range(65)]
    draw.polygon(outer + inner, fill=WHITE)

    draw.polygon(
        [
            (cx - 0.06 * h, shank_bot - 0.02 * h),
            (cx, shank_bot + 0.055 * h),
            (cx + 0.06 * h, shank_bot - 0.02 * h),
        ],
        fill=WHITE,
    )

    def fluke(sign: float) -> None:
        tip_deg = -82 * sign
        mid_r = (outer_r + inner_r) / 2
        base_o = pt(tip_deg, outer_r)
        base_i = pt(tip_deg, inner_r)
        tip_deg2 = tip_deg * 1.16
        tip = pt(tip_deg2, mid_r + 0.10 * h)
        draw.polygon([base_o, tip, base_i], fill=WHITE)

    fluke(1.0)
    fluke(-1.0)


def render(size: int) -> Image.Image:
    big = 1024
    img = Image.new("RGBA", (big, big), BLUE_DARK)
    # simple radial-ish wash so it reads like the glossy source icon
    pix = img.load()
    cx = cy = big / 2
    max_r = big * 0.72
    for y in range(big):
        for x in range(big):
            dx = (x - cx) / max_r
            dy = (y - cy - big * 0.08) / max_r
            t = min(1.0, math.sqrt(dx * dx + dy * dy))
            r = int(BLUE_LIGHT[0] * (1 - t) + BLUE_DARK[0] * t)
            g = int(BLUE_LIGHT[1] * (1 - t) + BLUE_DARK[1] * t)
            b = int(BLUE_LIGHT[2] * (1 - t) + BLUE_DARK[2] * t)
            pix[x, y] = (r, g, b, 255)
    draw = ImageDraw.Draw(img)
    # hole color sampled from center-ish field
    hole = pix[big // 2, big // 2]
    paint_anchor(draw, big / 2, big / 2 + big * 0.02, big * 0.72, hole)
    if size == big:
        return img
    return img.resize((size, size), Image.Resampling.LANCZOS)


def main() -> None:
    root = Path(__file__).resolve().parents[1] / "src" / "main" / "res"
    sizes = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    master = render(1024)
    for name, px in sizes.items():
        folder = root / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        im = master.resize((px, px), Image.Resampling.LANCZOS)
        im.save(folder / "ic_launcher.png")
        im.save(folder / "ic_launcher_round.png")
    print("wrote launcher mipmaps")


if __name__ == "__main__":
    main()
