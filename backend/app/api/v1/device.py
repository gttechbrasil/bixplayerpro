from datetime import UTC, datetime

from fastapi import APIRouter, Depends, Request, status
from sqlalchemy import func, select

from app.core.deps import CurrentDevice, DbSession, get_client_ip
from app.core.exceptions import bad_request, forbidden, not_found
from app.core.ratelimit import device_with_rate_limit, limit_device_register
from app.models import Device, Playlist
from app.schemas.common import Message
from app.schemas.device import (
    DeviceConfig,
    DeviceRegisterRequest,
    DeviceRegisterResponse,
    PlaylistCreate,
    PlaylistOut,
)
from app.services import audit
from app.services.devices import build_config, register_device
from app.services.playlists import apply_parsed, parse_playlist_url, playlist_url_for_app
from app.services.settings import get_all_settings

router = APIRouter(prefix="/device", tags=["device"])

MAX_PLAYLISTS_PER_DEVICE = 20
MSG_NOT_REGISTERED = "Dispositivo não cadastrado. Informe o MAC ao seu revendedor."


@router.post(
    "/register",
    summary="Registra o aparelho e devolve MAC + token",
    response_model=DeviceRegisterResponse,
    dependencies=[Depends(limit_device_register)],
)
async def register(body: DeviceRegisterRequest, db: DbSession) -> DeviceRegisterResponse:
    device, token = await register_device(db, body.device_id, body.app_type, body.app_version)
    await db.commit()
    return DeviceRegisterResponse(mac_address=device.mac_address, token=token)


@router.get(
    "/config",
    summary="Configuração completa para o app (playlists, tema, status)",
    response_model=DeviceConfig,
)
async def config(db: DbSession, device: Device = Depends(device_with_rate_limit)) -> DeviceConfig:
    device.last_seen_at = datetime.now(UTC)
    settings_values = await get_all_settings(db)
    result = await build_config(db, device, settings_values)
    await db.commit()
    return result


@router.post(
    "/playlists",
    summary="Adiciona playlist ao dispositivo (auto-cadastro pelo app)",
    response_model=PlaylistOut,
    status_code=status.HTTP_201_CREATED,
)
async def add_playlist(
    body: PlaylistCreate, device: CurrentDevice, db: DbSession, request: Request
) -> PlaylistOut:
    if not device.is_registered:
        raise forbidden(MSG_NOT_REGISTERED, "device_not_registered")
    count = await db.scalar(
        select(func.count()).select_from(Playlist).where(Playlist.device_id == device.id)
    )
    if count is not None and count >= MAX_PLAYLISTS_PER_DEVICE:
        raise bad_request("Limite de playlists por dispositivo atingido.", "playlist_limit")

    parsed = parse_playlist_url(body.url)
    playlist = Playlist(
        device_id=device.id, name=body.name, is_protected=body.is_protected, position=count or 0
    )
    apply_parsed(playlist, parsed)
    db.add(playlist)
    await db.flush()
    await audit.record(
        db,
        actor_type="device",
        actor_id=device.id,
        action="playlist.create",
        target=f"playlist:{playlist.id}",
        payload={"device_id": device.id, "name": playlist.name, "host": playlist.host},
        ip=get_client_ip(request),
    )
    await db.commit()
    return PlaylistOut(
        id=playlist.id,
        name=playlist.name,
        url=playlist_url_for_app(playlist),
        type=playlist.type,
        is_protected=playlist.is_protected,
    )


@router.delete(
    "/playlists/{playlist_id}", summary="Remove playlist do dispositivo", response_model=Message
)
async def delete_playlist(
    playlist_id: int, device: CurrentDevice, db: DbSession, request: Request
) -> Message:
    if not device.is_registered:
        raise forbidden(MSG_NOT_REGISTERED, "device_not_registered")
    playlist = await db.scalar(
        select(Playlist).where(Playlist.id == playlist_id, Playlist.device_id == device.id)
    )
    if playlist is None:
        raise not_found("Playlist não encontrada.")
    await db.delete(playlist)
    await audit.record(
        db,
        actor_type="device",
        actor_id=device.id,
        action="playlist.delete",
        target=f"playlist:{playlist_id}",
        payload={"device_id": device.id, "name": playlist.name},
        ip=get_client_ip(request),
    )
    await db.commit()
    return Message(message="Playlist removida.")
