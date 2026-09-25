#!/usr/bin/env python3
"""Install the captain-supplied glossy admiralty anchor as launcher icons.

Loads app/icons/source_*.b64 (joined) or source.png.b64, writes:
  - res/mipmap-*/ic_launcher.png          (exact artwork — My Files thumbnail)
  - res/mipmap-*/ic_launcher_round.png    (circular crop of same artwork)
  - res/drawable/ic_launcher_foreground.png
and deletes any leftover vector foreground XML so the PNG wins.
"""
from __future__ import annotations

import base64
from io import BytesIO
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent


def load_source() -> Image.Image:
    chunks = []
    i = 1
    while True:
        path = HERE / f"source_{i}.b64"
        if not path.exists():
            break
        chunks.append(path.read_text().strip())
        i += 1
    if chunks:
        raw = "".join(chunks)
    else:
        raw = (HERE / "source.png.b64").read_text().strip()
    raw = "".join(raw.split())
    raw += "=" * ((-len(raw)) % 4)
    data = base64.b64decode(raw)
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"decoded icon is not a PNG (got {data[:16]!r})")
    im = Image.open(BytesIO(data)).convert("RGBA")
    if min(im.size) < 96:
        raise SystemExit(f"source icon too small: {im.size}")
    return im


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
    print(f"source {src.size[0]}x{src.size[1]} {src.mode}")

    drawable = res / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    src.resize((432, 432), Image.Resampling.LANCZOS).save(
        drawable / "ic_launcher_foreground.png", optimize=True
    )
    xml_fg = drawable / "ic_launcher_foreground.xml"
    if xml_fg.exists():
        xml_fg.unlink()
        print("removed vector ic_launcher_foreground.xml")

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
