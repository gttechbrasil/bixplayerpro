"""Builds the Bix Player Pro brand assets from docs/brand/logo-original.png.

The original has a rasterised checkerboard, two faded bands and a sparkle watermark. All of
them are grey (R≈G≈B) while the logo is orange/yellow, so the mask comes from saturation:
alpha = sat(pixel) / sat(nearest opaque logo pixel), and the edge colour is un-mixed from the
local grey so no grey fringe remains.
"""

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(r"C:\Users\gustavo\Desktop\Projetos\projeto_lizandro")
BRAND = ROOT / "docs" / "brand"
RES = ROOT / "android" / "app" / "src" / "main" / "res"
WEB_ASSETS = ROOT / "web" / "src" / "lib" / "assets"
WEB_STATIC = ROOT / "web" / "static"

BG = (0x1F, 0x1F, 0x13)
ACCENT = (0xFF, 0x8A, 0x00)
FONT_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"  # Segoe UI Bold; on Linux/macOS point to any bold TTF
FONT_SEMI = r"C:\Windows\Fonts\seguisb.ttf"


def max_filter(arr: np.ndarray, size: int) -> np.ndarray:
    im = Image.fromarray(arr.astype(np.uint8))
    return np.array(im.filter(ImageFilter.MaxFilter(size)))


def extract_logo() -> Image.Image:
    src = np.array(Image.open(BRAND / "logo-original.png").convert("RGB")).astype(np.float32)
    mx = src.max(axis=2)
    mn = src.min(axis=2)
    sat = mx - mn  # 0 for grey (checkerboard, bands, watermark), high for the orange logo
    opaque = sat > 70
    # saturation of the nearest opaque pixel: max filter over the opaque region
    sat_ref = np.where(opaque, sat, 0)
    ref = max_filter(sat_ref.clip(0, 255), 9).astype(np.float32)
    ref = np.maximum(ref, 70)
    alpha = np.clip((sat - 4) / (ref - 4), 0, 1)
    alpha[opaque] = 1.0
    alpha[sat < 5] = 0.0
    # local grey estimate: mean of RGB where the pixel is background, filled outward
    grey = src.mean(axis=2)
    bg_known = alpha < 0.05
    bg_est = np.where(bg_known, grey, 0).astype(np.float32)
    weight = bg_known.astype(np.float32)
    for _ in range(12):
        # spread known background values into unknown pixels (box blur of value / weight)
        bimg = Image.fromarray(bg_est.clip(0, 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(2))
        wimg = Image.fromarray((weight * 255).clip(0, 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(2))
        b = np.array(bimg).astype(np.float32)
        w = np.array(wimg).astype(np.float32) / 255
        fill = np.where(w > 0.02, b / np.maximum(w, 0.02), 0)
        bg_est = np.where(weight > 0, bg_est, fill)
        weight = np.where(weight > 0, weight, (w > 0.02).astype(np.float32))
    bg_rgb = np.repeat(bg_est[..., None], 3, axis=2)
    a3 = np.repeat(alpha[..., None], 3, axis=2)
    fg = np.where(a3 > 0.02, (src - (1 - a3) * bg_rgb) / np.maximum(a3, 0.02), src)
    fg = fg.clip(0, 255)
    out = np.dstack([fg, alpha * 255]).astype(np.uint8)
    img = Image.fromarray(out, "RGBA")
    # drop stray specks (watermark remnants): keep only alpha inside the main component bbox
    bbox = Image.fromarray((alpha > 0.5).astype(np.uint8) * 255).getbbox()
    print("content bbox", bbox)
    return img.crop(bbox)


def pad_square(img: Image.Image, side: int, margin: float = 0.06) -> Image.Image:
    inner = int(side * (1 - 2 * margin))
    ratio = min(inner / img.width, inner / img.height)
    resized = img.resize((max(1, round(img.width * ratio)), max(1, round(img.height * ratio))), Image.LANCZOS)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(resized, ((side - resized.width) // 2, (side - resized.height) // 2), resized)
    return canvas


def fit_height(img: Image.Image, height: int) -> Image.Image:
    ratio = height / img.height
    return img.resize((round(img.width * ratio), height), Image.LANCZOS)


def wordmark(text: str, color, height: int, font_path=FONT_BOLD) -> Image.Image:
    font = ImageFont.truetype(font_path, height)
    l, t, r, b = font.getbbox(text)
    img = Image.new("RGBA", (r - l + 4, b - t + 4), (0, 0, 0, 0))
    ImageDraw.Draw(img).text((-l + 2, -t + 2), text, font=font, fill=color + (255,))
    return img


def lockup(symbol: Image.Image, text_color, height: int = 128) -> Image.Image:
    """Symbol + 'Bix Player Pro' side by side, transparent background."""
    sym = fit_height(symbol, height)
    word = wordmark("Bix Player Pro", text_color, int(height * 0.52))
    gap = int(height * 0.22)
    canvas = Image.new("RGBA", (sym.width + gap + word.width, height), (0, 0, 0, 0))
    canvas.paste(sym, (0, 0), sym)
    canvas.paste(word, (sym.width + gap, (height - word.height) // 2), word)
    return canvas


def rounded_mask(size: int, radius: int) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return m


def main() -> None:
    logo = extract_logo()
    BRAND.mkdir(exist_ok=True)
    square = pad_square(logo, 1024, margin=0.04)
    square.save(BRAND / "logo.png")
    print("logo.png", square.size)

    # Panel lock-ups: dark text for the light theme, light text for the dark theme.
    lockup(logo, (0x1F, 0x1F, 0x13), 160).save(BRAND / "logo-panel-light.png")
    lockup(logo, (0xF5, 0xF3, 0xEA), 160).save(BRAND / "logo-panel-dark.png")

    # Favicon: symbol on the brand background, rounded.
    for size, name in ((512, "favicon-512.png"), (192, "favicon-192.png"), (64, "favicon.png")):
        fav = Image.new("RGBA", (size, size), BG + (255,))
        sym = pad_square(logo, size, margin=0.12)
        fav.paste(sym, (0, 0), sym)
        fav.putalpha(rounded_mask(size, size // 5))
        fav.save(BRAND / name)
    (BRAND / "favicon.png").replace(BRAND / "favicon.png")

    # Android adaptive icon: 108dp canvas, content inside the 66dp safe zone (432px @ xxxhdpi).
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    sym = pad_square(logo, 432, margin=0.26)
    fg.paste(sym, (0, 0), sym)
    fg.save(BRAND / "ic_launcher_foreground.png")
    for density, dp in (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)):
        d = RES / f"mipmap-{density}"
        d.mkdir(exist_ok=True)
        side = int(108 * dp)
        fg.resize((side, side), Image.LANCZOS).save(d / "ic_launcher_foreground.png")
        legacy = int(48 * dp)
        icon = Image.new("RGBA", (legacy, legacy), BG + (255,))
        s = pad_square(logo, legacy, margin=0.14)
        icon.paste(s, (0, 0), s)
        squircle = icon.copy()
        squircle.putalpha(rounded_mask(legacy, legacy // 5))
        squircle.save(d / "ic_launcher.png")
        circle = icon.copy()
        m = Image.new("L", (legacy, legacy), 0)
        ImageDraw.Draw(m).ellipse((0, 0, legacy - 1, legacy - 1), fill=255)
        circle.putalpha(m)
        circle.save(d / "ic_launcher_round.png")

    # Android TV banner 320x180dp @ xhdpi = 640x360.
    banner = Image.new("RGBA", (640, 360), BG + (255,))
    lk = lockup(logo, (0xF5, 0xF3, 0xEA), 150)
    scale = min(560 / lk.width, 1)
    lk = lk.resize((round(lk.width * scale), round(lk.height * scale)), Image.LANCZOS)
    banner.paste(lk, ((640 - lk.width) // 2, (360 - lk.height) // 2), lk)
    (RES / "drawable-xhdpi").mkdir(exist_ok=True)
    banner.convert("RGB").save(RES / "drawable-xhdpi" / "app_banner.png")
    banner.save(BRAND / "tv-banner.png")

    # Default logo bundled in the app (shown when the reseller has not uploaded one).
    (RES / "drawable-xhdpi").mkdir(exist_ok=True)
    fit_height(lockup(logo, (0xF5, 0xF3, 0xEA), 160), 96).save(RES / "drawable-xhdpi" / "brand_logo.png")
    fit_height(logo, 512).save(RES / "drawable-xhdpi" / "brand_symbol.png")

    # Web
    WEB_ASSETS.mkdir(parents=True, exist_ok=True)
    (BRAND / "logo-panel-light.png").replace(BRAND / "logo-panel-light.png")
    Image.open(BRAND / "logo-panel-light.png").save(WEB_ASSETS / "logo-light.png")
    Image.open(BRAND / "logo-panel-dark.png").save(WEB_ASSETS / "logo-dark.png")
    fit_height(logo, 128).save(WEB_ASSETS / "symbol.png")
    Image.open(BRAND / "favicon.png").save(WEB_STATIC / "favicon.png")
    Image.open(BRAND / "favicon-192.png").save(WEB_STATIC / "icon-192.png")
    print("done")


if __name__ == "__main__":
    sys.exit(main())
