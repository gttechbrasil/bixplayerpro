from datetime import date
from typing import Literal

from fastapi import APIRouter, Depends, Query, Request, status
from sqlalchemy import or_, select
from sqlalchemy.orm import selectinload

from app.core.deps import CurrentReseller, DbSession, get_client_ip
from app.core.pagination import paginate
from app.models import Device
from app.schemas.common import Message, Page, PageParams
from app.schemas.reseller import (
    BatchDelete,
    BatchDeleteResult,
    ResellerDeviceCreate,
    ResellerDeviceOut,
    ResellerDeviceUpdate,
)
from app.services.reseller_devices import (
    create_device,
    delete_devices,
    device_to_out,
    get_own_device,
    update_device,
)

router = APIRouter(prefix="/devices", tags=["reseller: devices"])

StatusFilter = Literal["active", "expired"]


@router.get("", summary="Lista os dispositivos da revenda", response_model=Page[ResellerDeviceOut])
async def list_devices(
    reseller: CurrentReseller,
    db: DbSession,
    params: PageParams = Depends(),
    status_filter: StatusFilter | None = Query(None, alias="status"),
) -> Page[ResellerDeviceOut]:
    stmt = (
        select(Device)
        .where(Device.reseller_id == reseller.id)
        .options(selectinload(Device.playlists))
        .order_by(Device.created_at.desc(), Device.id.desc())
    )
    if params.search:
        term = f"%{params.search.strip()}%"
        stmt = stmt.where(or_(Device.mac_address.ilike(term), Device.client_name.ilike(term)))
    if status_filter == "expired":
        stmt = stmt.where(Device.license_expires_at < date.today())
    elif status_filter == "active":
        stmt = stmt.where(
            or_(Device.license_expires_at.is_(None), Device.license_expires_at >= date.today())
        )
    return await paginate(db, stmt, params, device_to_out)


@router.post(
    "",
    summary="Cadastra (ou reivindica) um dispositivo pelo MAC",
    response_model=ResellerDeviceOut,
    status_code=status.HTTP_201_CREATED,
)
async def create(
    body: ResellerDeviceCreate, reseller: CurrentReseller, db: DbSession, request: Request
) -> ResellerDeviceOut:
    device = await create_device(db, reseller, body, get_client_ip(request))
    await db.commit()
    return device_to_out(device)


@router.get("/{device_id}", summary="Detalhe do dispositivo", response_model=ResellerDeviceOut)
async def get(device_id: int, reseller: CurrentReseller, db: DbSession) -> ResellerDeviceOut:
    return device_to_out(await get_own_device(db, reseller, device_id))


@router.put("/{device_id}", summary="Edita o dispositivo", response_model=ResellerDeviceOut)
async def update(
    device_id: int,
    body: ResellerDeviceUpdate,
    reseller: CurrentReseller,
    db: DbSession,
    request: Request,
) -> ResellerDeviceOut:
    device = await get_own_device(db, reseller, device_id)
    device = await update_device(db, reseller, device, body, get_client_ip(request))
    await db.commit()
    return device_to_out(device)


@router.delete("/{device_id}", summary="Exclui o dispositivo", response_model=Message)
async def delete(
    device_id: int, reseller: CurrentReseller, db: DbSession, request: Request
) -> Message:
    await get_own_device(db, reseller, device_id)
    await delete_devices(db, reseller, [device_id], get_client_ip(request))
    await db.commit()
    return Message(message="Dispositivo excluído.")


@router.post(
    "/batch-delete", summary="Exclui vários dispositivos", response_model=BatchDeleteResult
)
async def batch_delete(
    body: BatchDelete, reseller: CurrentReseller, db: DbSession, request: Request
) -> BatchDeleteResult:
    deleted = await delete_devices(db, reseller, body.ids, get_client_ip(request))
    await db.commit()
    return BatchDeleteResult(deleted=deleted, message=f"{deleted} dispositivo(s) excluído(s).")
