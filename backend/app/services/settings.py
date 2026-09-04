"""Global settings stored as key/value JSON rows."""

from typing import Any

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Setting

DEFAULT_SETTINGS: dict[str, Any] = {
    "monthly_price": "35.00",
    "packages": [],
    "min_app_version": "1.0.0",
    "apk_url": "",
    "platform_name": "Plataforma",
    "credits_enabled": False,
}


async def get_all_settings(db: AsyncSession) -> dict[str, Any]:
    rows = (await db.scalars(select(Setting))).all()
    values = dict(DEFAULT_SETTINGS)
    values.update({row.key: row.value for row in rows})
    return values


async def get_setting(db: AsyncSession, key: str) -> Any:
    row = await db.get(Setting, key)
    return row.value if row is not None else DEFAULT_SETTINGS.get(key)


async def credits_enabled(db: AsyncSession) -> bool:
    return bool(await get_setting(db, "credits_enabled"))


async def set_settings(db: AsyncSession, values: dict[str, Any]) -> dict[str, Any]:
    for key, value in values.items():
        row = await db.get(Setting, key)
        if row is None:
            db.add(Setting(key=key, value=value))
        else:
            row.value = value
    await db.flush()
    return await get_all_settings(db)
