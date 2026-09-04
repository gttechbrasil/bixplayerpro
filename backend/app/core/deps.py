"""FastAPI dependencies: authentication for the three actors and CSRF protection."""

from datetime import date
from typing import Annotated

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings, get_settings
from app.core.exceptions import forbidden, unauthorized
from app.core.security import decode_access_token, hash_token
from app.db.session import get_db
from app.models import Admin, Device, Reseller

ADMIN_COOKIE = "admin_session"
RESELLER_COOKIE = "reseller_session"
SAFE_METHODS = {"GET", "HEAD", "OPTIONS"}

MSG_RESELLER_BLOCKED = "Revenda bloqueada. Entre em contato com o administrador."
MSG_RESELLER_EXPIRED = "Revenda vencida. Renove para continuar."

_bearer = HTTPBearer(auto_error=False)


def get_client_ip(request: Request) -> str | None:
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()[:45]
    return request.client.host[:45] if request.client else None


def _check_csrf(request: Request, settings: Settings) -> None:
    if request.method in SAFE_METHODS:
        return
    cookie = request.cookies.get(settings.csrf_cookie_name)
    header = request.headers.get(settings.csrf_header_name)
    if not cookie or not header or cookie != header:
        raise forbidden("Token CSRF inválido. Recarregue a página e tente novamente.", "csrf")


def _subject_from_cookie(request: Request, cookie_name: str, role: str) -> int:
    token = request.cookies.get(cookie_name)
    if not token:
        raise unauthorized()
    payload = decode_access_token(token)
    if payload is None or payload.get("role") != role:
        raise unauthorized("Sessão expirada. Faça login novamente.", "session_expired")
    try:
        return int(payload["sub"])
    except (KeyError, ValueError):
        raise unauthorized() from None


async def current_admin(
    request: Request,
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> Admin:
    admin_id = _subject_from_cookie(request, ADMIN_COOKIE, "admin")
    admin = await db.get(Admin, admin_id)
    if admin is None:
        raise unauthorized()
    _check_csrf(request, settings)
    return admin


def ensure_reseller_active(reseller: Reseller, today: date | None = None) -> None:
    today = today or date.today()
    if reseller.is_blocked:
        raise forbidden(MSG_RESELLER_BLOCKED, "reseller_blocked")
    if reseller.has_expired(today):
        raise forbidden(MSG_RESELLER_EXPIRED, "reseller_expired")


async def current_reseller(
    request: Request,
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> Reseller:
    reseller_id = _subject_from_cookie(request, RESELLER_COOKIE, "reseller")
    reseller = await db.get(Reseller, reseller_id)
    if reseller is None:
        raise unauthorized()
    ensure_reseller_active(reseller)
    _check_csrf(request, settings)
    return reseller


async def current_reseller_allow_expired(
    request: Request,
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> Reseller:
    """Like `current_reseller` but lets an expired reseller through (renewal/profile)."""
    reseller_id = _subject_from_cookie(request, RESELLER_COOKIE, "reseller")
    reseller = await db.get(Reseller, reseller_id)
    if reseller is None:
        raise unauthorized()
    if reseller.is_blocked:
        raise forbidden(MSG_RESELLER_BLOCKED, "reseller_blocked")
    _check_csrf(request, settings)
    return reseller


async def current_device(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
    db: AsyncSession = Depends(get_db),
) -> Device:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise unauthorized("Token do dispositivo ausente.", "device_token_missing")
    device = await Device.by_token_hash(db, hash_token(credentials.credentials))
    if device is None:
        raise unauthorized("Token do dispositivo inválido.", "device_token_invalid")
    return device


CurrentAdmin = Annotated[Admin, Depends(current_admin)]
CurrentReseller = Annotated[Reseller, Depends(current_reseller)]
CurrentResellerAllowExpired = Annotated[Reseller, Depends(current_reseller_allow_expired)]
CurrentDevice = Annotated[Device, Depends(current_device)]
DbSession = Annotated[AsyncSession, Depends(get_db)]
