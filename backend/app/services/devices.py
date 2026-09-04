"""Device registration and app configuration."""

from datetime import UTC, date, datetime
from typing import Any

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.security import (
    generate_mac_address,
    generate_opaque_token,
    hash_device_identifier,
    hash_token,
)
from app.models import Banner, Device, Reseller
from app.schemas.device import BannerOut, DeviceConfig, PlaylistOut
from app.services.playlists import playlist_url_for_app

MAC_GENERATION_ATTEMPTS = 10


async def allocate_mac_address(db: AsyncSession) -> str:
    for _ in range(MAC_GENERATION_ATTEMPTS):
        mac = generate_mac_address()
        exists = await db.scalar(select(Device.id).where(Device.mac_address == mac))
        if exists is None:
            return mac
    raise RuntimeError("could not allocate a unique MAC address")  # pragma: no cover


async def register_device(
    db: AsyncSession, raw_device_id: str, app_type: str, app_version: str
) -> tuple[Device, str]:
    """Creates the device on first contact, otherwise returns it. Always rotates the token."""
    device_hash = hash_device_identifier(raw_device_id)
    device = await db.scalar(select(Device).where(Device.device_id == device_hash))

    if device is None:
        device = Device(device_id=device_hash, mac_address=await allocate_mac_address(db))
        db.add(device)

    token = generate_opaque_token()
    device.token_hash = hash_token(token)
    device.app_type = app_type
    device.app_version = app_version or None
    device.last_seen_at = datetime.now(UTC)
    await db.flush()
    return device, token


def device_status(device: Device, reseller: Reseller | None, today: date) -> str:
    if reseller is None:
        return "unregistered"
    if not reseller.is_active(today) or device.license_expired(today):
        return "expired"
    return "active"


async def load_device_full(db: AsyncSession, device_id: int) -> Device:
    result = await db.scalar(
        select(Device)
        .where(Device.id == device_id)
        .options(selectinload(Device.playlists), selectinload(Device.reseller))
    )
    assert result is not None
    return result


async def build_config(
    db: AsyncSession, device: Device, settings_values: dict[str, Any]
) -> DeviceConfig:
    today = date.today()
    device = await load_device_full(db, device.id)
    reseller = device.reseller
    status = device_status(device, reseller, today)

    playlists: list[PlaylistOut] = []
    banners: list[BannerOut] = []
    if status == "active" and reseller is not None:
        playlists = [
            PlaylistOut(
                id=p.id,
                name=p.name,
                url=playlist_url_for_app(p),
                type=p.type,
                is_protected=p.is_protected,
            )
            for p in device.playlists
        ]
        rows = await db.scalars(
            select(Banner)
            .where(Banner.reseller_id == reseller.id, Banner.is_active.is_(True))
            .order_by(Banner.id)
        )
        banners = [BannerOut.model_validate(b) for b in rows]

    return DeviceConfig(
        registered=reseller is not None,
        mac_address=device.mac_address,
        status=status,
        client_name=device.client_name,
        license_expires_at=device.license_expires_at,
        playlists=playlists,
        theme=reseller.theme if reseller else "default",
        logo_url=reseller.logo_url if reseller else None,
        bg_url=reseller.bg_url if reseller else None,
        qr_content=reseller.qr_content if reseller else None,
        banners=banners,
        auto_ads=reseller.auto_ads if reseller else False,
        pin=device.pin,
        min_app_version=str(settings_values.get("min_app_version", "")),
        apk_url=str(settings_values.get("apk_url", "")),
        platform_name=str(settings_values.get("platform_name", "")),
    )
