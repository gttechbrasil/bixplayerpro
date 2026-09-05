import re

from app.core.security import (
    create_access_token,
    decode_access_token,
    decrypt_secret,
    encrypt_secret,
    generate_mac_address,
    hash_password,
    hash_token,
    verify_password,
)


def test_password_hashing() -> None:
    h = hash_password("segredo")
    assert h.startswith("$argon2id$")
    assert verify_password("segredo", h)
    assert not verify_password("errado", h)


def test_jwt_roundtrip() -> None:
    token = create_access_token(42, "admin")
    payload = decode_access_token(token)
    assert payload is not None
    assert payload["sub"] == "42"
    assert payload["role"] == "admin"
    assert decode_access_token(token + "x") is None


def test_fernet_roundtrip() -> None:
    enc = encrypt_secret("senha-xtream")
    assert enc != "senha-xtream"
    assert decrypt_secret(enc) == "senha-xtream"


def test_mac_generation() -> None:
    mac = generate_mac_address()
    assert re.fullmatch(r"02:50:50:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}", mac)
    assert hash_token("abc") == hash_token("abc")
    assert len(hash_token("abc")) == 64


def test_cors_origins_parsing() -> None:
    """An empty CORS_ORIGINS in the .env must be valid (it broke the first deploy)."""
    from app.core.config import Settings

    assert Settings(cors_origins="").cors_origin_list == []
    assert Settings(cors_origins="https://a.com, https://b.com ").cors_origin_list == [
        "https://a.com",
        "https://b.com",
    ]
