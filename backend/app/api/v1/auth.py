from datetime import date
from typing import Literal

from fastapi import APIRouter, Depends, Query, Request, Response
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings, get_settings
from app.core.deps import (
    ADMIN_COOKIE,
    MSG_RESELLER_BLOCKED,
    RESELLER_COOKIE,
    get_client_ip,
)
from app.core.exceptions import forbidden, unauthorized
from app.core.security import create_access_token, decode_access_token, generate_csrf_token
from app.db.session import get_db
from app.models import Admin, Reseller
from app.schemas.auth import (
    AdminLoginResponse,
    AdminOut,
    LoginRequest,
    MeResponse,
    PlatformInfo,
    ResellerLoginResponse,
    ResellerMe,
)
from app.schemas.common import Message
from app.services.auth import authenticate_admin, authenticate_reseller
from app.services.settings import get_all_settings

router = APIRouter(prefix="/auth", tags=["auth"])


def _reseller_me(reseller: Reseller) -> ResellerMe:
    me = ResellerMe.model_validate(reseller)
    me.is_expired = reseller.has_expired(date.today())
    return me


async def _platform(db: AsyncSession) -> PlatformInfo:
    values = await get_all_settings(db)
    return PlatformInfo(
        name=str(values.get("platform_name", "")),
        credits_enabled=bool(values.get("credits_enabled", False)),
    )


def _set_session_cookies(
    response: Response, settings: Settings, cookie_name: str, token: str
) -> str:
    max_age = settings.jwt_expire_minutes * 60
    response.set_cookie(
        cookie_name,
        token,
        max_age=max_age,
        httponly=True,
        secure=settings.cookie_secure,
        samesite="lax",
        path="/",
    )
    csrf = generate_csrf_token()
    response.set_cookie(
        settings.csrf_cookie_name,
        csrf,
        max_age=max_age,
        httponly=False,
        secure=settings.cookie_secure,
        samesite="lax",
        path="/",
    )
    return csrf


@router.post("/admin/login", summary="Login do administrador", response_model=AdminLoginResponse)
async def admin_login(
    body: LoginRequest,
    request: Request,
    response: Response,
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> AdminLoginResponse:
    admin = await authenticate_admin(db, body.username, body.password, get_client_ip(request))
    token = create_access_token(admin.id, "admin")
    csrf = _set_session_cookies(response, settings, ADMIN_COOKIE, token)
    return AdminLoginResponse(
        user=AdminOut.model_validate(admin), csrf_token=csrf, platform=await _platform(db)
    )


@router.post("/reseller/login", summary="Login da revenda", response_model=ResellerLoginResponse)
async def reseller_login(
    body: LoginRequest,
    request: Request,
    response: Response,
    db: AsyncSession = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> ResellerLoginResponse:
    reseller = await authenticate_reseller(db, body.username, body.password, get_client_ip(request))
    # Expired resellers may log in to renew; blocked ones may not.
    if reseller.is_blocked:
        raise forbidden(MSG_RESELLER_BLOCKED, "reseller_blocked")
    token = create_access_token(reseller.id, "reseller")
    csrf = _set_session_cookies(response, settings, RESELLER_COOKIE, token)
    return ResellerLoginResponse(
        user=_reseller_me(reseller), csrf_token=csrf, platform=await _platform(db)
    )


@router.post("/logout", summary="Encerra a sessão", response_model=Message)
async def logout(response: Response, settings: Settings = Depends(get_settings)) -> Message:
    for name in (ADMIN_COOKIE, RESELLER_COOKIE, settings.csrf_cookie_name):
        response.delete_cookie(name, path="/")
    return Message(message="Sessão encerrada.")


@router.get("/me", summary="Ator autenticado na sessão atual", response_model=MeResponse)
async def me(
    request: Request,
    db: AsyncSession = Depends(get_db),
    prefer: Literal["admin", "reseller"] | None = Query(
        None,
        alias="as",
        description="Sessão a preferir quando o navegador tem as duas (admin e revenda).",
    ),
) -> MeResponse:
    """Returns the logged-in actor. Admin and reseller sessions live in different cookies and
    may coexist in one browser (the platform owner opening a reseller panel, or a reseller
    who was handed the admin login). Each panel asks for its own role with `?as=`; without
    it the admin session wins, as before (M5-024)."""
    order = ("reseller", "admin") if prefer == "reseller" else ("admin", "reseller")
    for role in order:
        found = await _session_actor(request, db, role)
        if found is not None:
            return found
    raise unauthorized()


async def _session_actor(request: Request, db: AsyncSession, role: str) -> MeResponse | None:
    cookie = ADMIN_COOKIE if role == "admin" else RESELLER_COOKIE
    token = request.cookies.get(cookie)
    payload = decode_access_token(token) if token else None
    if not payload or payload.get("role") != role:
        return None
    if role == "admin":
        admin = await db.get(Admin, int(payload["sub"]))
        if admin is None:
            return None
        return MeResponse(
            role="admin", user=AdminOut.model_validate(admin), platform=await _platform(db)
        )
    reseller = await db.get(Reseller, int(payload["sub"]))
    if reseller is None:
        return None
    if reseller.is_blocked:
        raise forbidden(MSG_RESELLER_BLOCKED, "reseller_blocked")
    return MeResponse(role="reseller", user=_reseller_me(reseller), platform=await _platform(db))
