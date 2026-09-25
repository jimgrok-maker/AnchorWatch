#!/usr/bin/env python3
"""Paint a classic gold admiralty anchor on navy and write launcher mipmaps."""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

NAVY = (11, 28, 36, 255)
GOLD = (212, 168, 72, 255)


def _ellipse(draw: ImageDraw.ImageDraw, cx, cy, r, fill):
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill)


def _round_rect(draw: ImageDraw.ImageDraw, x0, y0, x1, y1, fill):
    r = max(1, int((y1 - y0) / 2))
    draw.rounded_rectangle((x0, y0, x1, y1), radius=r, fill=fill)


def paint_anchor(draw: ImageDraw.ImageDraw, cx: float, cy: float, h: float) -> None:
    ring_cy = cy - 0.38 * h
    _ellipse(draw, cx, ring_cy, 0.125 * h, GOLD)
    _ellipse(draw, cx, ring_cy, 0.068 * h, NAVY)

    shank_w = 0.072 * h
    shank_top = ring_cy + 0.04 * h
    shank_bot = cy + 0.36 * h
    draw.rectangle((cx - shank_w / 2, shank_top, cx + shank_w / 2, shank_bot), fill=GOLD)

    stock_w = 0.42 * h
    stock_h = 0.07 * h
    stock_cy = cy - 0.20 * h
    _round_rect(
        draw,
        cx - stock_w / 2,
        stock_cy - stock_h / 2,
        cx + stock_w / 2,
        stock_cy + stock_h / 2,
        GOLD,
    )

    arm_cy = cy + 0.02 * h
    outer_r = 0.34 * h
    inner_r = 0.235 * h

    def pt(deg, r):
        rad = math.radians(deg)
        return (cx + r * math.sin(rad), arm_cy + r * math.cos(rad))

    outer = [pt(-82 + i * (164 / 64), outer_r) for i in range(65)]
    inner = [pt(82 - i * (164 / 64), inner_r) for i in range(65)]
    draw.polygon(outer + inner, fill=GOLD)

    draw.polygon(
        [
            (cx - 0.06 * h, shank_bot - 0.02 * h),
            (cx, shank_bot + 0.055 * h),
            (cx + 0.06 * h, shank_bot - 0.02 * h),
        ],
        fill=GOLD,
    )

    def fluke(sign: float) -> None:
        tip_deg = -82 * sign
        mid_r = (outer_r + inner_r) / 2
        base_o = pt(tip_deg, outer_r)
        base_i = pt(tip_deg, inner_r)
        tip_deg2 = tip_deg * 1.16
        tip = pt(tip_deg2, mid_r + 0.10 * h)
        draw.polygon([base_o, tip, base_i], fill=GOLD)

    fluke(1.0)
    fluke(-1.0)


def render(size: int) -> Image.Image:
    big = 1024
    img = Image.new("RGBA", (big, big), NAVY)
    draw = ImageDraw.Draw(img)
    paint_anchor(draw, big / 2, big / 2 + big * 0.02, big * 0.72)
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
