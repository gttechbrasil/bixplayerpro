from typing import Literal

from fastapi import APIRouter, File, Request, UploadFile, status
from sqlalchemy import func, select

from app.core.deps import CurrentReseller, DbSession, get_client_ip
from app.core.exceptions import bad_request, not_found
from app.models import Banner
from app.schemas.common import Message
from app.schemas.reseller import (
    BannerCreate,
    BannerUpdate,
    BrandingOut,
    BrandingUpdate,
    ResellerBannerOut,
    UploadResult,
)
from app.services import audit
from app.services.uploads import MAX_UPLOAD_BYTES, MSG_TOO_LARGE, save_image

router = APIRouter(prefix="/branding", tags=["reseller: branding"])

MAX_BANNERS = 10
MSG_BANNER_LIMIT = f"Limite de {MAX_BANNERS} banners por revenda atingido."
MSG_BANNER_NOT_FOUND = "Banner não encontrado."


@router.get(
    "", summary="Personalização do app (logo, fundo, QR, layout)", response_model=BrandingOut
)
async def get_branding(reseller: CurrentReseller) -> BrandingOut:
    return BrandingOut.model_validate(reseller)


@router.put("", summary="Atualiza a personalização", response_model=BrandingOut)
async def update_branding(
    body: BrandingUpdate, reseller: CurrentReseller, db: DbSession, request: Request
) -> BrandingOut:
    changes = body.model_dump(exclude_unset=True)
    for key, value in changes.items():
        setattr(reseller, key, value)
    await audit.record(
        db,
        actor_type="reseller",
        actor_id=reseller.id,
        action="branding.update",
        target=f"reseller:{reseller.id}",
        payload=changes,
        ip=get_client_ip(request),
    )
    await db.commit()
    return BrandingOut.model_validate(reseller)


@router.post(
    "/upload",
    summary="Envia imagem de logo ou fundo (PNG/JPG/WebP, até 2 MB)",
    response_model=UploadResult,
)
async def upload_image(
    reseller: CurrentReseller,
    db: DbSession,
    request: Request,
    kind: Literal["logo", "bg"] = "logo",
    file: UploadFile = File(...),
) -> UploadResult:
    data = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(data) > MAX_UPLOAD_BYTES:
        raise bad_request(MSG_TOO_LARGE, "upload_too_large")
    url = save_image(data, reseller.id, kind)
    if kind == "logo":
        reseller.logo_url = url
    else:
        reseller.bg_url = url
    await audit.record(
        db,
        actor_type="reseller",
        actor_id=reseller.id,
        action="branding.upload",
        target=f"reseller:{reseller.id}",
        payload={"kind": kind, "url": url, "bytes": len(data)},
        ip=get_client_ip(request),
    )
    await db.commit()
    return UploadResult(url=url, kind=kind)


# ---- banners ---------------------------------------------------------------------
async def _own_banner(db: DbSession, reseller_id: int, banner_id: int) -> Banner:
    banner = await db.scalar(
        select(Banner).where(Banner.id == banner_id, Banner.reseller_id == reseller_id)
    )
    if banner is None:
        raise not_found(MSG_BANNER_NOT_FOUND)
    return banner


@router.get("/banners", summary="Lista banners", response_model=list[ResellerBannerOut])
async def list_banners(reseller: CurrentReseller, db: DbSession) -> list[ResellerBannerOut]:
    rows = await db.scalars(
        select(Banner).where(Banner.reseller_id == reseller.id).order_by(Banner.id)
    )
    return [ResellerBannerOut.model_validate(b) for b in rows]


@router.post(
    "/banners",
    summary="Cria banner",
    response_model=ResellerBannerOut,
    status_code=status.HTTP_201_CREATED,
)
async def create_banner(
    body: BannerCreate, reseller: CurrentReseller, db: DbSession, request: Request
) -> ResellerBannerOut:
    count = await db.scalar(
        select(func.count()).select_from(Banner).where(Banner.reseller_id == reseller.id)
    )
    if (count or 0) >= MAX_BANNERS:
        raise bad_request(MSG_BANNER_LIMIT, "banner_limit")
    if not body.url.lower().startswith(("http://", "https://")):
        raise bad_request("URL do banner inválida.", "invalid_url")
    banner = Banner(
        reseller_id=reseller.id, title=body.title, url=body.url, is_active=body.is_active
    )
    db.add(banner)
    await db.flush()
    await audit.record(
        db,
        actor_type="reseller",
        actor_id=reseller.id,
        action="banner.create",
        target=f"banner:{banner.id}",
        payload={"title": banner.title, "url": banner.url},
        ip=get_client_ip(request),
    )
    await db.commit()
    return ResellerBannerOut.model_validate(banner)


@router.patch(
    "/banners/{banner_id}",
    summary="Edita banner (título, URL, ativo)",
    response_model=ResellerBannerOut,
)
async def update_banner(
    banner_id: int,
    body: BannerUpdate,
    reseller: CurrentReseller,
    db: DbSession,
    request: Request,
) -> ResellerBannerOut:
    banner = await _own_banner(db, reseller.id, banner_id)
    changes = body.model_dump(exclude_unset=True)
    if "url" in changes and not str(changes["url"]).lower().startswith(("http://", "https://")):
        raise bad_request("URL do banner inválida.", "invalid_url")
    for key, value in changes.items():
        setattr(banner, key, value)
    await audit.record(
        db,
        actor_type="reseller",
        actor_id=reseller.id,
        action="banner.update",
        target=f"banner:{banner.id}",
        payload=changes,
        ip=get_client_ip(request),
    )
    await db.commit()
    return ResellerBannerOut.model_validate(banner)


@router.delete("/banners/{banner_id}", summary="Exclui banner", response_model=Message)
async def delete_banner(
    banner_id: int, reseller: CurrentReseller, db: DbSession, request: Request
) -> Message:
    banner = await _own_banner(db, reseller.id, banner_id)
    await db.delete(banner)
    await audit.record(
        db,
        actor_type="reseller",
        actor_id=reseller.id,
        action="banner.delete",
        target=f"banner:{banner_id}",
        payload={"title": banner.title},
        ip=get_client_ip(request),
    )
    await db.commit()
    return Message(message="Banner excluído.")
