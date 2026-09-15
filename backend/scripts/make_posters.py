"""Generate placeholder poster/backdrop art for the dev fixture.

The fixture used to point every movie and series at the platform logo, so any screen that shows
artwork (the cinema/rail/mosaic home layouts, F2-002) looked empty in development. This writes a
handful of distinct posters and one backdrop into `uploads/posters/`.

    py -3.12 backend/scripts/make_posters.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parents[1] / "uploads" / "posters"

# Sky top, sky bottom, sun, ridge far, ridge near, water — the same moods the app's demo mode draws.
MOODS = [
    ((0x1B, 0x3A, 0x5C), (0xE0, 0x8A, 0x4E), (0xFF, 0xD2, 0x7A), (0x2E, 0x4A, 0x6B), (0x1C, 0x2E, 0x44), (0x24, 0x4B, 0x6E)),
    ((0x0F, 0x2A, 0x3F), (0x6F, 0xA8, 0xDC), (0xFF, 0xF3, 0xC4), (0x3E, 0x6B, 0x8C), (0x24, 0x4A, 0x66), (0x1E, 0x4D, 0x6B)),
    ((0x3A, 0x1C, 0x4E), (0xE2, 0x64, 0x7A), (0xFF, 0xC9, 0x8B), (0x4C, 0x2F, 0x5E), (0x2A, 0x1A, 0x36), (0x5B, 0x2E, 0x5E)),
    ((0x12, 0x32, 0x1F), (0x9C, 0xC7, 0x7A), (0xFF, 0xF0, 0xA8), (0x2F, 0x6B, 0x3E), (0x1B, 0x44, 0x26), (0x28, 0x5C, 0x4C)),
    ((0x2B, 0x2B, 0x4A), (0x8E, 0x7C, 0xC3), (0xFF, 0xE4, 0xB5), (0x3F, 0x3F, 0x6E), (0x26, 0x26, 0x4A), (0x34, 0x34, 0x5C)),
    ((0x4A, 0x2A, 0x12), (0xE7, 0xA4, 0x5B), (0xFF, 0xE8, 0xA3), (0x7A, 0x4A, 0x24), (0x4E, 0x2F, 0x17), (0x6B, 0x4A, 0x2A)),
    ((0x10, 0x28, 0x33), (0x4F, 0xB3, 0xA8), (0xE9, 0xFF, 0xD8), (0x1F, 0x5A, 0x57), (0x12, 0x39, 0x38), (0x17, 0x4F, 0x52)),
    ((0x33, 0x14, 0x22), (0xC8, 0x5A, 0x54), (0xFF, 0xD9, 0xA0), (0x5C, 0x28, 0x33), (0x35, 0x17, 0x1F), (0x6E, 0x32, 0x3C)),
]


def landscape(draw: ImageDraw.ImageDraw, w: int, h: int, mood: tuple, sun_x: float) -> None:
    sky_top, sky_bottom, sun, far, near, water = mood
    for y in range(h):
        t = y / max(1, h - 1)
        draw.line(
            [(0, y), (w, y)],
            fill=tuple(round(sky_top[i] + (sky_bottom[i] - sky_top[i]) * t) for i in range(3)),
        )
    r = h * 0.12
    cx, cy = w * sun_x, h * 0.30
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=sun)
    draw.polygon(
        [(0, h * 0.62), (w * 0.25, h * 0.38), (w * 0.45, h * 0.58), (w * 0.70, h * 0.34), (w, h * 0.60), (w, h), (0, h)],
        fill=far,
    )
    draw.polygon(
        [(0, h * 0.78), (w * 0.20, h * 0.58), (w * 0.42, h * 0.74), (w * 0.62, h * 0.52), (w * 0.85, h * 0.72), (w, h * 0.66), (w, h), (0, h)],
        fill=near,
    )
    draw.rectangle([0, h * 0.84, w, h], fill=water)


def font(size: int) -> ImageFont.ImageFont:
    for name in ("segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for i, mood in enumerate(MOODS, start=1):
        img = Image.new("RGB", (400, 600))
        d = ImageDraw.Draw(img)
        landscape(d, 400, 600, mood, 0.25 + (i % 4) * 0.18)
        d.rectangle([0, 470, 400, 600], fill=(0, 0, 0))
        d.text((24, 500), f"Amostra {i}", font=font(40), fill=(255, 255, 255))
        d.text((24, 552), "fixture", font=font(24), fill=(200, 200, 200))
        img.save(OUT / f"p{i}.png")

    wide = Image.new("RGB", (1280, 720))
    landscape(ImageDraw.Draw(wide), 1280, 720, MOODS[0], 0.68)
    wide.save(OUT / "backdrop.png")
    print("wrote", len(MOODS), "posters and one backdrop to", OUT)


if __name__ == "__main__":
    main()
