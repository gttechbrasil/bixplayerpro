from fastapi import APIRouter, Depends, Request, Response
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings, get_settings
from app.core.deps import (
    ADMIN_COOKIE,
    RESELLER_COOKIE,
    ensure_reseller_active,
    get_client_ip,
)
from app.core.exceptions import unauthorized
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
    ensure_reseller_active(reseller)
    token = create_access_token(reseller.id, "reseller")
    csrf = _set_session_cookies(response, settings, RESELLER_COOKIE, token)
    return ResellerLoginResponse(
        user=ResellerMe.model_validate(reseller), csrf_token=csrf, platform=await _platform(db)
    )


@router.post("/logout", summary="Encerra a sessão", response_model=Message)
async def logout(response: Response, settings: Settings = Depends(get_settings)) -> Message:
    for name in (ADMIN_COOKIE, RESELLER_COOKIE, settings.csrf_cookie_name):
        response.delete_cookie(name, path="/")
    return Message(message="Sessão encerrada.")


@router.get("/me", summary="Ator autenticado na sessão atual", response_model=MeResponse)
async def me(request: Request, db: AsyncSession = Depends(get_db)) -> MeResponse:
    """Returns the logged-in actor. Checks the admin cookie first, then the reseller one."""
    admin_token = request.cookies.get(ADMIN_COOKIE)
    payload = decode_access_token(admin_token) if admin_token else None
    if payload and payload.get("role") == "admin":
        admin = await db.get(Admin, int(payload["sub"]))
        if admin is not None:
            return MeResponse(
                role="admin", user=AdminOut.model_validate(admin), platform=await _platform(db)
            )

    reseller_token = request.cookies.get(RESELLER_COOKIE)
    payload = decode_access_token(reseller_token) if reseller_token else None
    if payload and payload.get("role") == "reseller":
        reseller = await db.get(Reseller, int(payload["sub"]))
        if reseller is not None:
            ensure_reseller_active(reseller)
            return MeResponse(
                role="reseller",
                user=ResellerMe.model_validate(reseller),
                platform=await _platform(db),
            )

    raise unauthorized()
