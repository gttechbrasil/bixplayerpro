"""Diagnostics bundles sent by the app (M5-013): device side (POST) and admin side (read)."""

from datetime import datetime
from typing import Any, Literal

from fastapi import APIRouter, Depends, status
from pydantic import BaseModel, Field
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.deps import CurrentAdmin, DbSession, current_device
from app.core.exceptions import not_found
from app.core.ratelimit import consume
from app.models import Device
from app.models.diagnostic import DeviceDiagnostic
from app.schemas.common import ORMModel

MAX_BODY_CHARS = 512 * 1024
KEEP_PER_DEVICE = 10
DIAGNOSTICS_PER_WINDOW = 10

device_router = APIRouter(prefix="/device", tags=["device"])
admin_router = APIRouter(prefix="/resellers", tags=["admin: resellers"])


class DiagnosticCreate(BaseModel):
    kind: Literal["crash", "manual"] = "manual"
    app_version: str | None = Field(None, max_length=32)
    device_info: dict[str, Any] | None = None
    log: str = Field(min_length=1, max_length=MAX_BODY_CHARS)


class DiagnosticCreated(BaseModel):
    id: int
    message: str


class DiagnosticOut(ORMModel):
    id: int
    device_id: int
    kind: str
    app_version: str | None
    device_info: dict[str, Any] | None
    size: int
    created_at: datetime


class DiagnosticDetail(DiagnosticOut):
    body: str


async def _limit(device: Device = Depends(current_device)) -> Device:
    # At most DIAGNOSTICS_PER_WINDOW bundles per device every 10 rate-limit windows (10 min).
    await consume(
        f"rl:diag:dev:{device.id}", DIAGNOSTICS_PER_WINDOW, get_settings().device_rate_window * 10
    )
    return device


@device_router.post(
    "/diagnostics",
    summary="Envia um pacote de diagnóstico (crash ou manual) para o painel",
    response_model=DiagnosticCreated,
    status_code=status.HTTP_201_CREATED,
)
async def create_diagnostic(
    body: DiagnosticCreate, db: DbSession, device: Device = Depends(_limit)
) -> DiagnosticCreated:
    entry = DeviceDiagnostic(
        device_id=device.id,
        kind=body.kind,
        app_version=body.app_version or device.app_version,
        device_info=body.device_info,
        size=len(body.log.encode("utf-8")),
        body=body.log,
    )
    db.add(entry)
    await db.flush()
    await _trim(db, device.id)
    await db.commit()
    return DiagnosticCreated(id=entry.id, message="Diagnóstico enviado. Obrigado!")


async def _trim(db: AsyncSession, device_id: int) -> None:
    """Keeps only the newest KEEP_PER_DEVICE bundles of a device."""
    ids = (
        await db.scalars(
            select(DeviceDiagnostic.id)
            .where(DeviceDiagnostic.device_id == device_id)
            .order_by(DeviceDiagnostic.id.desc())
            .offset(KEEP_PER_DEVICE)
        )
    ).all()
    if ids:
        await db.execute(delete(DeviceDiagnostic).where(DeviceDiagnostic.id.in_(ids)))


async def _device_of_reseller(db: AsyncSession, reseller_id: int, device_id: int) -> Device:
    device = await db.scalar(
        select(Device).where(Device.id == device_id, Device.reseller_id == reseller_id)
    )
    if device is None:
        raise not_found("Dispositivo não encontrado.")
    return device


@admin_router.get(
    "/{reseller_id}/devices/{device_id}/diagnostics",
    summary="Diagnósticos enviados pelo app deste dispositivo (mais recentes primeiro)",
    response_model=list[DiagnosticOut],
)
async def list_diagnostics(
    reseller_id: int, device_id: int, _: CurrentAdmin, db: DbSession
) -> list[DiagnosticOut]:
    await _device_of_reseller(db, reseller_id, device_id)
    rows = await db.scalars(
        select(DeviceDiagnostic)
        .where(DeviceDiagnostic.device_id == device_id)
        .order_by(DeviceDiagnostic.id.desc())
    )
    return [DiagnosticOut.model_validate(r) for r in rows]


@admin_router.get(
    "/{reseller_id}/devices/{device_id}/diagnostics/{diagnostic_id}",
    summary="Conteúdo completo de um diagnóstico",
    response_model=DiagnosticDetail,
)
async def get_diagnostic(
    reseller_id: int, device_id: int, diagnostic_id: int, _: CurrentAdmin, db: DbSession
) -> DiagnosticDetail:
    await _device_of_reseller(db, reseller_id, device_id)
    row = await db.scalar(
        select(DeviceDiagnostic).where(
            DeviceDiagnostic.id == diagnostic_id, DeviceDiagnostic.device_id == device_id
        )
    )
    if row is None:
        raise not_found("Diagnóstico não encontrado.")
    return DiagnosticDetail.model_validate(row)
