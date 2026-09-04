from fastapi import APIRouter, Request
from pydantic import BaseModel, Field
from sqlalchemy import func, select

from app.core.deps import CurrentReseller, DbSession, get_client_ip
from app.core.exceptions import ApiError
from app.models import Device, Playlist
from app.services import audit
from app.services.playlists import normalize_host, replace_host

router = APIRouter(prefix="/dns", tags=["reseller: dns"])

MSG_SAME_HOST = "A DNS de origem e a de destino são iguais. Nenhuma alteração feita."


class DnsHost(BaseModel):
    host: str
    playlists: int


class DnsMigrateRequest(BaseModel):
    from_host: str = Field(min_length=3, max_length=255)
    to_host: str = Field(min_length=3, max_length=255)


class DnsMigrateResult(BaseModel):
    from_host: str
    to_host: str
    affected: int
    message: str


@router.get("", summary="Hosts (DNS) em uso nas playlists da revenda", response_model=list[DnsHost])
async def list_hosts(reseller: CurrentReseller, db: DbSession) -> list[DnsHost]:
    rows = await db.execute(
        select(Playlist.host, func.count(Playlist.id))
        .join(Device, Device.id == Playlist.device_id)
        .where(Device.reseller_id == reseller.id, Playlist.host.is_not(None))
        .group_by(Playlist.host)
        .order_by(func.count(Playlist.id).desc(), Playlist.host)
    )
    return [DnsHost(host=host, playlists=count) for host, count in rows.all()]


@router.post(
    "/migrate", summary="Substitui o host em todas as playlists", response_model=DnsMigrateResult
)
async def migrate(
    body: DnsMigrateRequest, reseller: CurrentReseller, db: DbSession, request: Request
) -> DnsMigrateResult:
    from_host = normalize_host(body.from_host)
    to_host = normalize_host(body.to_host)
    if from_host == to_host:
        raise ApiError(422, MSG_SAME_HOST, "same_host")

    playlists = (
        await db.scalars(
            select(Playlist)
            .join(Device, Device.id == Playlist.device_id)
            .where(Device.reseller_id == reseller.id, Playlist.host == from_host)
        )
    ).all()
    for playlist in playlists:
        playlist.url = replace_host(playlist.url, to_host)
        playlist.host = to_host
    await db.flush()

    await audit.record(
        db,
        actor_type="reseller",
        actor_id=reseller.id,
        action="dns.migrate",
        target=f"reseller:{reseller.id}",
        payload={"from_host": from_host, "to_host": to_host, "affected": len(playlists)},
        ip=get_client_ip(request),
    )
    await db.commit()
    return DnsMigrateResult(
        from_host=from_host,
        to_host=to_host,
        affected=len(playlists),
        message=f"DNS migrada com sucesso. {len(playlists)} playlist(s) atualizada(s).",
    )
