"""Build the stock backgrounds the panel offers to every reseller (F2-008).

The client's own art lives in docs/brand ("BACKGROUND NN.png"); this script converts it to the
1920x1080 JPEGs the panel points at, and generates a placeholder for any slot that has no art yet.
Replacing a background is therefore: drop the PNG in docs/brand, map it below, run the script and
`./deploy/push-backgrounds.sh`. bg1 is also the app's default when a reseller has not chosen one.

    py -3.12 backend/scripts/make_backgrounds.py
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
BRAND = ROOT / "docs" / "brand"
OUT = ROOT / "backend" / "uploads" / "backgrounds"
W, H = 1920, 1080

# Slot -> client art. "BACKGROUND 06" is the red one he asked to be the default (24/09/2026).
ART = {
    "bg1": "BACKGROUND 06.png",
    "bg2": "BACKGROUND 03.png",
}

# Base (#050404) with the brand orange (#FF8A00) used sparingly: the home draws white titles and
# orange focus rings over these, so the placeholder art stays dark and low-contrast on purpose.
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


PLACEHOLDERS = {"bg1": aurora, "bg2": spotlight, "bg3": grid}


def from_art(name: str) -> Image.Image:
    """The client's PNG as an opaque 1920x1080 frame: flattened on black, cover-scaled, centred."""
    src = Image.open(BRAND / name).convert("RGBA")
    flat = Image.new("RGBA", src.size, (0, 0, 0, 255))
    flat.alpha_composite(src)
    img = flat.convert("RGB")
    scale = max(W / img.width, H / img.height)
    img = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
    left, top = (img.width - W) // 2, (img.height - H) // 2
    return img.crop((left, top, left + W, top + H))


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for slot, build in PLACEHOLDERS.items():
        art = ART.get(slot)
        img = from_art(art) if art else build()
        img.save(OUT / f"{slot}.jpg", quality=88, optimize=True)
        print("wrote", OUT / f"{slot}.jpg", img.size, "from", art or f"placeholder {build.__name__}")


if __name__ == "__main__":
    main()
