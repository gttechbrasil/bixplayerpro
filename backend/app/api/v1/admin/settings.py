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
        monthly_price=Decimal(str(values.get("monthly_price", "0"))),
        packages=[Package.model_validate(p) for p in values.get("packages", [])],
        min_app_version=str(values.get("min_app_version", "")),
        apk_url=str(values.get("apk_url", "")),
        platform_name=str(values.get("platform_name", "")),
    )


@router.get("", response_model=SettingsOut)
async def get_settings_(_: CurrentAdmin, db: DbSession) -> SettingsOut:
    return _to_out(await get_all_settings(db))


@router.put("", response_model=SettingsOut)
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
    await audit.record(
        db,
        actor_type="admin",
        actor_id=admin.id,
        action="settings.update",
        target="settings",
        payload=changes,
        ip=get_client_ip(request),
    )
    await db.commit()
    return _to_out(values)
