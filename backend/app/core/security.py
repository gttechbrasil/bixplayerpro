"""Password hashing, JWT sessions, opaque device tokens, secret encryption, MAC generation."""

import hashlib
import secrets
from datetime import UTC, datetime, timedelta
from typing import Any, Literal

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import VerificationError, VerifyMismatchError
from cryptography.fernet import Fernet, InvalidToken

from app.core.config import get_settings

Role = Literal["admin", "reseller"]

_hasher = PasswordHasher()


# ---- passwords -------------------------------------------------------------
def hash_password(password: str) -> str:
    return _hasher.hash(password)


def verify_password(password: str, password_hash: str) -> bool:
    try:
        return _hasher.verify(password_hash, password)
    except (VerifyMismatchError, VerificationError):
        return False


# ---- JWT (admin / reseller sessions) ---------------------------------------
def create_access_token(subject: int, role: Role, expires_minutes: int | None = None) -> str:
    settings = get_settings()
    now = datetime.now(UTC)
    payload: dict[str, Any] = {
        "sub": str(subject),
        "role": role,
        "iat": int(now.timestamp()),
        "exp": now + timedelta(minutes=expires_minutes or settings.jwt_expire_minutes),
    }
    return jwt.encode(payload, settings.secret_key, algorithm=settings.jwt_algorithm)


def decode_access_token(token: str) -> dict[str, Any] | None:
    settings = get_settings()
    try:
        return jwt.decode(token, settings.secret_key, algorithms=[settings.jwt_algorithm])
    except jwt.PyJWTError:
        return None


# ---- opaque tokens (devices) & CSRF ----------------------------------------
def generate_opaque_token() -> str:
    return secrets.token_hex(32)


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def generate_csrf_token() -> str:
    return secrets.token_urlsafe(32)


def hash_device_identifier(raw: str) -> str:
    return hashlib.sha256(raw.strip().encode()).hexdigest()


# ---- symmetric encryption for secrets at rest ------------------------------
def _fernet() -> Fernet:
    key = get_settings().fernet_key
    if not key:
        raise RuntimeError("FERNET_KEY is not configured")
    return Fernet(key.encode())


def encrypt_secret(value: str) -> str:
    return _fernet().encrypt(value.encode()).decode()


def decrypt_secret(value: str) -> str:
    try:
        return _fernet().decrypt(value.encode()).decode()
    except InvalidToken as exc:
        raise RuntimeError("Unable to decrypt stored secret (FERNET_KEY changed?)") from exc


# ---- MAC generation ----------------------------------------------------------
def generate_mac_address() -> str:
    prefix = get_settings().mac_prefix
    suffix = ":".join(f"{b:02X}" for b in secrets.token_bytes(3))
    return f"{prefix}:{suffix}"
