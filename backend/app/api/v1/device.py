import hmac
from datetime import UTC, date, datetime, timedelta

from fastapi import APIRouter, Depends, Request, status
from sqlalchemy import func, select

from app.core.deps import CurrentDevice, DbSession, get_client_ip
from app.core.exceptions import bad_request, conflict, forbidden, not_found
from app.core.ratelimit import consume, device_with_rate_limit, limit_device_register
from app.models import Device, Playlist, Reseller
from app.schemas.common import Message
from app.schemas.device import (
    DeviceConfig,
    DeviceRegisterRequest,
    DeviceRegisterResponse,
    PlaylistCreate,
    PlaylistOut,
    ReviewCodeRequest,
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
async def config(
    request: Request, db: DbSession, device: Device = Depends(device_with_rate_limit)
) -> DeviceConfig:
    device.last_seen_at = datetime.now(UTC)
    _note_app_build(device, request)
    settings_values = await get_all_settings(db)
    result = await build_config(db, device, settings_values)
    await db.commit()
    return result


def _note_app_build(device: Device, request: Request) -> None:
    """Keeps app_version/app_type current from the headers the app sends on every call
    (M5-022): before this they were only written on register, so the admin showed the
    version the device had when it was first activated."""
    version = (request.headers.get("x-app-version") or "").strip()[:32]
    app_type = (request.headers.get("x-app-type") or "").strip().lower()
    if version and version != device.app_version:
        device.app_version = version
    if app_type in ("tv", "mobile") and app_type != device.app_type:
        device.app_type = app_type


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


# ---- Google Play review (ADR-007) --------------------------------------------------------------
REVIEW_LICENSE_DAYS = 14
REVIEW_ATTEMPTS = 5
REVIEW_WINDOW_SECONDS = 3600
REVIEW_CLIENT_NAME = "Revisão Google Play"
REVIEW_PLAYLIST_NAME = "Demonstração"
MSG_REVIEW_INVALID = "Código inválido."
MSG_REVIEW_TAKEN = "Este aparelho já está ativado por um revendedor."


async def _review_reseller(db: DbSession, values: dict) -> Reseller | None:
    username = str(values.get("play_review_reseller") or "").strip()
    if not username:
        return None
    reseller = await db.scalar(select(Reseller).where(Reseller.username == username))
    if reseller is None or reseller.is_blocked:
        return None
    return reseller


@router.post(
    "/review-code",
    summary="Ativa o aparelho com o código de revisão da Google Play",
    response_model=Message,
)
async def review_code(
    body: ReviewCodeRequest, device: CurrentDevice, db: DbSession, request: Request
) -> Message:
    """The store build has no reseller behind the reviewer's device: a code set by the admin
    links it to the demo reseller for a few days, with the demo list. No credit is spent."""
    ip = get_client_ip(request)
    await consume(f"rl:review:dev:{device.id}", REVIEW_ATTEMPTS, REVIEW_WINDOW_SECONDS)
    await consume(f"rl:review:ip:{ip or '-'}", REVIEW_ATTEMPTS * 4, REVIEW_WINDOW_SECONDS)

    values = await get_all_settings(db)
    expected = str(values.get("play_review_code") or "").strip()
    given = body.code.strip()
    reseller = await _review_reseller(db, values)
    if (
        not expected
        or reseller is None
        or not hmac.compare_digest(given.encode(), expected.encode())
    ):
        raise forbidden(MSG_REVIEW_INVALID, "review_code_invalid")
    if device.reseller_id is not None and device.reseller_id != reseller.id:
        raise conflict(MSG_REVIEW_TAKEN, "device_already_registered")

    device.reseller_id = reseller.id
    device.client_name = device.client_name or REVIEW_CLIENT_NAME
    device.license_expires_at = date.today() + timedelta(days=REVIEW_LICENSE_DAYS)
    playlist_url = str(values.get("play_review_playlist_url") or "").strip()
    existing = await db.scalar(
        select(func.count()).select_from(Playlist).where(Playlist.device_id == device.id)
    )
    if playlist_url and not existing:
        playlist = Playlist(device_id=device.id, name=REVIEW_PLAYLIST_NAME, position=0)
        apply_parsed(playlist, parse_playlist_url(playlist_url))
        db.add(playlist)
    await db.flush()
    await audit.record(
        db,
        actor_type="device",
        actor_id=device.id,
        action="device.review_activate",
        target=f"device:{device.id}",
        payload={
            "reseller_id": reseller.id,
            "license_expires_at": str(device.license_expires_at),
        },
        ip=ip,
    )
    await db.commit()
    return Message(message="Aparelho ativado para avaliação.")
