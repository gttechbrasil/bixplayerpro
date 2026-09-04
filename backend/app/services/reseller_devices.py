"""Device management on behalf of a reseller (claim/create, edit, delete)."""

import re
from datetime import date

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.core.exceptions import bad_request, conflict, not_found
from app.models import Device, Playlist, Reseller
from app.schemas.reseller import ResellerDeviceCreate, ResellerDeviceOut, ResellerDeviceUpdate
from app.services import audit
from app.services.credits import adjust_credits
from app.services.playlists import apply_parsed, parse_playlist_url, playlist_url_for_app
from app.services.settings import credits_enabled

MSG_MAC_INVALID = "MAC inválido. Use o formato AA:BB:CC:DD:EE:FF."
MSG_MAC_OWN = "Este MAC já está cadastrado na sua conta."
MSG_MAC_OTHER = "Este MAC já pertence a outro revendedor."
MSG_DEVICE_NOT_FOUND = "Dispositivo não encontrado."

_HEX12 = re.compile(r"^[0-9A-F]{12}$")


def normalize_mac(raw: str) -> str:
    digits = re.sub(r"[^0-9A-Fa-f]", "", raw).upper()
    if not _HEX12.fullmatch(digits):
        raise bad_request(MSG_MAC_INVALID, "invalid_mac")
    return ":".join(digits[i : i + 2] for i in range(0, 12, 2))


def device_to_out(device: Device, today: date | None = None) -> ResellerDeviceOut:
    today = today or date.today()
    first = device.playlists[0] if device.playlists else None
    out = ResellerDeviceOut.model_validate(device)
    out.playlist_name = first.name if first else None
    out.playlist_url = playlist_url_for_app(first) if first else None
    out.playlist_host = first.host if first else None
    out.playlists_count = len(device.playlists)
    out.connected = device.device_id is not None
    out.status = "expired" if device.license_expired(today) else "active"
    return out


def _device_query(reseller_id: int):
    return (
        select(Device)
        .where(Device.reseller_id == reseller_id)
        .options(selectinload(Device.playlists))
    )


async def get_own_device(db: AsyncSession, reseller: Reseller, device_id: int) -> Device:
    device = await db.scalar(_device_query(reseller.id).where(Device.id == device_id))
    if device is None:
        raise not_found(MSG_DEVICE_NOT_FOUND)
    return device


async def create_device(
    db: AsyncSession, reseller: Reseller, body: ResellerDeviceCreate, ip: str | None
) -> Device:
    mac = normalize_mac(body.mac_address)
    parsed = parse_playlist_url(body.playlist_url)

    device = await db.scalar(
        select(Device).where(Device.mac_address == mac).options(selectinload(Device.playlists))
    )
    if device is not None:
        if device.reseller_id == reseller.id:
            raise conflict(MSG_MAC_OWN, "mac_already_yours")
        if device.reseller_id is not None:
            raise conflict(MSG_MAC_OTHER, "mac_taken")

    if await credits_enabled(db):
        await adjust_credits(
            db,
            reseller,
            -1,
            reason="device_registration",
            ref=f"mac:{mac}",
            actor_type="reseller",
            actor_id=reseller.id,
            ip=ip,
        )

    claimed = device is not None
    if device is None:
        device = Device(mac_address=mac)
        db.add(device)
    device.reseller_id = reseller.id
    device.client_name = body.client_name
    device.license_expires_at = body.license_expires_at
    await db.flush()

    playlist = Playlist(device_id=device.id, name=body.playlist_name, position=0)
    apply_parsed(playlist, parsed)
    db.add(playlist)
    await db.flush()

    await audit.record(
        db,
        actor_type="reseller",
        actor_id=reseller.id,
        action="device.create",
        target=f"device:{device.id}",
        payload={
            "mac_address": mac,
            "client_name": body.client_name,
            "claimed": claimed,
            "host": playlist.host,
            "license_expires_at": str(body.license_expires_at),
        },
        ip=ip,
    )
    await db.refresh(device, attribute_names=["playlists"])
    return device


async def update_device(
    db: AsyncSession, reseller: Reseller, device: Device, body: ResellerDeviceUpdate, ip: str | None
) -> Device:
    parsed = parse_playlist_url(body.playlist_url)
    device.client_name = body.client_name
    device.license_expires_at = body.license_expires_at

    playlist = device.playlists[0] if device.playlists else None
    if playlist is None:
        playlist = Playlist(device_id=device.id, position=0)
        db.add(playlist)
    playlist.name = body.playlist_name
    apply_parsed(playlist, parsed)
    await db.flush()

    await audit.record(
        db,
        actor_type="reseller",
        actor_id=reseller.id,
        action="device.update",
        target=f"device:{device.id}",
        payload={
            "mac_address": device.mac_address,
            "client_name": body.client_name,
            "host": playlist.host,
            "license_expires_at": str(body.license_expires_at),
        },
        ip=ip,
    )
    await db.refresh(device, attribute_names=["playlists"])
    return device


async def delete_devices(
    db: AsyncSession, reseller: Reseller, ids: list[int], ip: str | None
) -> int:
    devices = (
        await db.scalars(
            select(Device).where(Device.reseller_id == reseller.id, Device.id.in_(ids))
        )
    ).all()
    for device in devices:
        await audit.record(
            db,
            actor_type="reseller",
            actor_id=reseller.id,
            action="device.delete",
            target=f"device:{device.id}",
            payload={"mac_address": device.mac_address, "client_name": device.client_name},
            ip=ip,
        )
        await db.delete(device)
    await db.flush()
    return len(devices)


async def count_devices(db: AsyncSession, reseller_id: int) -> int:
    return (
        await db.scalar(
            select(func.count()).select_from(Device).where(Device.reseller_id == reseller_id)
        )
        or 0
    )
