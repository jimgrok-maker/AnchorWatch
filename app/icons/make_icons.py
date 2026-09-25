#!/usr/bin/env python3
"""Install the captain-supplied glossy anchor as launcher mipmaps + adaptive FG."""
from __future__ import annotations

import base64
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent


def load_source() -> Image.Image:
    raw = (HERE / "source.png.b64").read_text().strip()
    return Image.open(BytesIO(base64.b64decode(raw))).convert("RGBA")


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
    src = load_source()
    rnd = make_round(src)

    drawable = res / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    src.resize((432, 432), Image.Resampling.LANCZOS).save(
        drawable / "ic_launcher_foreground.png", optimize=True
    )
    xml_fg = drawable / "ic_launcher_foreground.xml"
    if xml_fg.exists():
        xml_fg.unlink()

    sizes = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }
    for name, px in sizes.items():
        folder = res / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        src.resize((px, px), Image.Resampling.LANCZOS).save(folder / "ic_launcher.png")
        rnd.resize((px, px), Image.Resampling.LANCZOS).save(folder / "ic_launcher_round.png")
    print("wrote supplied glossy launcher icon")


if __name__ == "__main__":
    main()
