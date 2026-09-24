"""Generate the stock backgrounds the panel offers to every reseller (F2-008).

Placeholders until the client sends his own art: three 1920x1080 images in the brand palette,
dark enough for the app's white text and the orange accent to stay readable on top. Replacing
them is a file swap — the panel points at fixed URLs.

    py -3.12 backend/scripts/make_backgrounds.py
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[1] / "uploads" / "backgrounds"
W, H = 1920, 1080

# Base (#050404) with the brand orange (#FF8A00) used sparingly: the home draws white titles and
# orange focus rings over these, so the art stays dark and low-contrast on purpose.
BASE = (0x05, 0x04, 0x04)
ORANGE = (0xFF, 0x8A, 0x00)


def vertical(img: Image.Image, top: tuple, bottom: tuple) -> None:
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        d.line([(0, y), (W, y)], fill=tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))


def glow(img: Image.Image, cx: float, cy: float, radius: float, colour: tuple, strength: float) -> None:
    """A soft radial light, drawn on its own layer and blurred so the edges never band."""
    layer = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=colour)
    layer = layer.filter(ImageFilter.GaussianBlur(radius * 0.55))
    img.paste(Image.blend(img, Image.blend(img, layer, 1.0), strength), (0, 0))


def aurora() -> Image.Image:
    """Diagonal ribbons of light over the near-black base."""
    img = Image.new("RGB", (W, H), BASE)
    vertical(img, (0x0B, 0x0A, 0x12), BASE)
    layer = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i, (colour, offset, thickness) in enumerate(
        ((ORANGE, -200, 90), ((0x8C, 0x46, 0x00), 120, 140), ((0x33, 0x1A, 0x00), 420, 220)),
    ):
        points = [(x, offset + x * 0.42 + math.sin(x / 260 + i) * 70) for x in range(0, W + 40, 40)]
        d.line(points, fill=colour, width=thickness, joint="curve")
    layer = layer.filter(ImageFilter.GaussianBlur(120))
    return Image.blend(img, layer, 0.5)


def spotlight() -> Image.Image:
    """One warm light in the upper right, the calmest of the three."""
    img = Image.new("RGB", (W, H), BASE)
    vertical(img, (0x12, 0x0C, 0x08), (0x03, 0x03, 0x03))
    glow(img, W * 0.78, H * 0.22, 620, (0x7A, 0x3D, 0x00), 0.55)
    glow(img, W * 0.2, H * 0.9, 520, (0x14, 0x12, 0x1C), 0.5)
    return img


def grid() -> Image.Image:
    """A faint perspective grid: reads as "tech" without competing with the menu."""
    img = Image.new("RGB", (W, H), BASE)
    vertical(img, (0x08, 0x07, 0x0A), (0x02, 0x02, 0x03))
    layer = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(layer)
    horizon = H * 0.58
    for i in range(-14, 15):
        d.line([(W / 2 + i * 150, H), (W / 2 + i * 26, horizon)], fill=(0x2A, 0x16, 0x06), width=3)
    step, y = 16, horizon
    while y < H:
        d.line([(0, y), (W, y)], fill=(0x2A, 0x16, 0x06), width=2)
        step *= 1.32
        y += step
    layer = layer.filter(ImageFilter.GaussianBlur(2))
    img = Image.blend(img, layer, 0.5)
    glow(img, W / 2, horizon, 700, (0x5A, 0x2C, 0x00), 0.35)
    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, build in (("bg1", aurora), ("bg2", spotlight), ("bg3", grid)):
        img = build()
        img.save(OUT / f"{name}.jpg", quality=86, optimize=True)
        print("wrote", OUT / f"{name}.jpg", img.size)


if __name__ == "__main__":
    main()
