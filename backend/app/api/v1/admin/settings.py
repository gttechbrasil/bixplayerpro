from decimal import Decimal
from typing import Any

from fastapi import APIRouter, Request

from app.core.deps import CurrentAdmin, DbSession, get_client_ip
from app.schemas.admin import Package, SettingsOut, SettingsUpdate
from app.services import audit
from app.services.settings import get_all_settings, set_settings

router = APIRouter(prefix="/settings", tags=["admin: settings"])


def _to_out(values: dict[str, Any]) -> SettingsOut:
    return SettingsOut(
        credits_enabled=bool(values.get("credits_enabled", False)),
        monthly_price=Decimal(str(values.get("monthly_price", "0"))),
        packages=[Package.model_validate(p) for p in values.get("packages", [])],
        min_app_version=str(values.get("min_app_version", "")),
        apk_url=str(values.get("apk_url", "")),
        platform_name=str(values.get("platform_name", "")),
        play_review_code=str(values.get("play_review_code") or ""),
        play_review_reseller=str(values.get("play_review_reseller") or ""),
        play_review_playlist_url=str(values.get("play_review_playlist_url") or ""),
    )


@router.get("", summary="Configurações globais", response_model=SettingsOut)
async def get_settings_(_: CurrentAdmin, db: DbSession) -> SettingsOut:
    return _to_out(await get_all_settings(db))


@router.put("", summary="Atualiza configurações globais", response_model=SettingsOut)
async def update_settings(
    body: SettingsUpdate, admin: CurrentAdmin, db: DbSession, request: Request
) -> SettingsOut:
    changes = body.model_dump(exclude_unset=True, mode="json")
    if "monthly_price" in changes:
        changes["monthly_price"] = f"{body.monthly_price:.2f}"
    if "packages" in changes and body.packages is not None:
        changes["packages"] = [
            {"months": p.months, "price": f"{p.price:.2f}"} for p in body.packages
        ]
    values = await set_settings(db, changes)
    logged = dict(changes)
    if logged.get("play_review_code"):
        logged["play_review_code"] = "***"  # it opens a device: keep it out of the audit log
    await audit.record(
        db,
        actor_type="admin",
        actor_id=admin.id,
        action="settings.update",
        target="settings",
        payload=logged,
        ip=get_client_ip(request),
    )
    await db.commit()
    return _to_out(values)
