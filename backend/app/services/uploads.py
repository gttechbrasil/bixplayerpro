"""Image uploads (logo / background) stored on a local volume served by Caddy."""

import secrets
from pathlib import Path

from app.core.config import get_settings
from app.core.exceptions import bad_request

MAX_UPLOAD_BYTES = 2 * 1024 * 1024
MSG_TOO_LARGE = "Imagem muito grande. O limite é 2 MB."
MSG_BAD_TYPE = "Formato não suportado. Envie PNG, JPG ou WebP."

_SIGNATURES: list[tuple[bytes, str, str]] = [
    (b"\x89PNG\r\n\x1a\n", "png", "image/png"),
    (b"\xff\xd8\xff", "jpg", "image/jpeg"),
]


def detect_image(data: bytes) -> tuple[str, str]:
    """Returns (extension, content type) from the magic bytes, or raises 400."""
    for magic, ext, ctype in _SIGNATURES:
        if data.startswith(magic):
            return ext, ctype
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "webp", "image/webp"
    raise bad_request(MSG_BAD_TYPE, "unsupported_image")


def upload_dir() -> Path:
    path = Path(get_settings().upload_dir)
    path.mkdir(parents=True, exist_ok=True)
    return path


def save_image(data: bytes, reseller_id: int, kind: str) -> str:
    """Validates and stores the image; returns its public URL."""
    if len(data) > MAX_UPLOAD_BYTES:
        raise bad_request(MSG_TOO_LARGE, "upload_too_large")
    ext, _ = detect_image(data)
    name = f"r{reseller_id}-{kind}-{secrets.token_hex(8)}.{ext}"
    (upload_dir() / name).write_bytes(data)
    base = get_settings().public_base_url.rstrip("/")
    return f"{base}/uploads/{name}"
