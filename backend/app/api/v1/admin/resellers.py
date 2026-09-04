from typing import Literal

from fastapi import APIRouter, Depends, Query, Request, status
from sqlalchemy import func, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import selectinload

from app.core.deps import CurrentAdmin, DbSession, get_client_ip
from app.core.exceptions import bad_request, conflict, not_found
from app.core.pagination import paginate
from app.core.security import hash_password
from app.models import CreditLedger, Device, Payment, Reseller
from app.schemas.admin import (
    BlockUpdate,
    CreditAdjust,
    ExpirationUpdate,
    LedgerOut,
    PasswordReset,
    PaymentOut,
    ResellerCreate,
    ResellerOut,
    ResellerUpdate,
)
from app.schemas.common import Message, Page, PageParams
from app.schemas.reseller import ResellerDeviceOut
from app.services import audit
from app.services.credits import adjust_credits
from app.services.reseller_devices import device_to_out
from app.services.settings import credits_enabled

router = APIRouter(prefix="/resellers", tags=["admin: resellers"])

MSG_USERNAME_TAKEN = "Já existe uma revenda com este usuário."
MSG_NOT_FOUND = "Revenda não encontrada."
MSG_CREDITS_DISABLED = "O sistema de créditos está desativado nas configurações."

StatusFilter = Literal["active", "blocked", "expired"]


def _devices_count_subquery():
    return (
        select(Device.reseller_id, func.count(Device.id).label("devices_count"))
        .group_by(Device.reseller_id)
        .subquery()
    )


def _to_out(row) -> ResellerOut:
    reseller, devices_count = row
    out = ResellerOut.model_validate(reseller)
    out.devices_count = devices_count or 0
    return out


async def _ensure_username_free(
    db: DbSession, username: str, exclude_id: int | None = None
) -> None:
    stmt = select(Reseller.id).where(Reseller.username == username)
    if exclude_id is not None:
        stmt = stmt.where(Reseller.id != exclude_id)
    if await db.scalar(stmt) is not None:
        raise conflict(MSG_USERNAME_TAKEN, "username_taken")


async def _get_reseller(db: DbSession, reseller_id: int) -> Reseller:
    reseller = await db.get(Reseller, reseller_id)
    if reseller is None:
        raise not_found(MSG_NOT_FOUND)
    return reseller


async def _reseller_out(db: DbSession, reseller: Reseller) -> ResellerOut:
    count = await db.scalar(
        select(func.count()).select_from(Device).where(Device.reseller_id == reseller.id)
    )
    return _to_out((reseller, count))


@router.get(
    "",
    summary="Lista revendas com busca, paginação e filtro de status",
    response_model=Page[ResellerOut],
)
async def list_resellers(
    _: CurrentAdmin,
    db: DbSession,
    params: PageParams = Depends(),
    status_filter: StatusFilter | None = Query(None, alias="status"),
) -> Page[ResellerOut]:
    counts = _devices_count_subquery()
    stmt = (
        select(Reseller, counts.c.devices_count)
        .outerjoin(counts, counts.c.reseller_id == Reseller.id)
        .order_by(Reseller.id.desc())
    )
    if params.search:
        term = f"%{params.search.strip()}%"
        stmt = stmt.where(or_(Reseller.username.ilike(term), Reseller.name.ilike(term)))
    today = func.current_date()
    if status_filter == "blocked":
        stmt = stmt.where(Reseller.is_blocked.is_(True))
    elif status_filter == "expired":
        stmt = stmt.where(Reseller.expires_at.is_not(None), Reseller.expires_at < today)
    elif status_filter == "active":
        stmt = stmt.where(
            Reseller.is_blocked.is_(False),
            or_(Reseller.expires_at.is_(None), Reseller.expires_at >= today),
        )
    return await paginate(db, stmt, params, _to_out)


@router.post(
    "", summary="Cria revenda", response_model=ResellerOut, status_code=status.HTTP_201_CREATED
)
async def create_reseller(
    body: ResellerCreate, admin: CurrentAdmin, db: DbSession, request: Request
) -> ResellerOut:
    await _ensure_username_free(db, body.username)
    reseller = Reseller(
        username=body.username,
        name=body.name,
        password_hash=hash_password(body.password),
        credits=0,
        expires_at=body.expires_at,
    )
    db.add(reseller)
    try:
        await db.flush()
    except IntegrityError:
        await db.rollback()
        raise conflict(MSG_USERNAME_TAKEN, "username_taken") from None
    await audit.record(
        db,
        actor_type="admin",
        actor_id=admin.id,
        action="reseller.create",
        target=f"reseller:{reseller.id}",
        payload={"username": reseller.username, "expires_at": str(body.expires_at)},
        ip=get_client_ip(request),
    )
    if body.credits and await credits_enabled(db):
        await adjust_credits(
            db,
            reseller,
            body.credits,
            reason="admin_adjustment",
            note="Créditos iniciais",
            actor_type="admin",
            actor_id=admin.id,
            ip=get_client_ip(request),
        )
    await db.commit()
    return await _reseller_out(db, reseller)


@router.get("/{reseller_id}", summary="Detalhe da revenda", response_model=ResellerOut)
async def get_reseller(reseller_id: int, _: CurrentAdmin, db: DbSession) -> ResellerOut:
    return await _reseller_out(db, await _get_reseller(db, reseller_id))


@router.patch("/{reseller_id}", summary="Edita dados da revenda", response_model=ResellerOut)
async def update_reseller(
    reseller_id: int, body: ResellerUpdate, admin: CurrentAdmin, db: DbSession, request: Request
) -> ResellerOut:
    reseller = await _get_reseller(db, reseller_id)
    changes = body.model_dump(exclude_unset=True)
    if "username" in changes:
        await _ensure_username_free(db, changes["username"], exclude_id=reseller.id)
    for key, value in changes.items():
        setattr(reseller, key, value)
    try:
        await db.flush()
    except IntegrityError:
        await db.rollback()
        raise conflict(MSG_USERNAME_TAKEN, "username_taken") from None
    await audit.record(
        db,
        actor_type="admin",
        actor_id=admin.id,
        action="reseller.update",
        target=f"reseller:{reseller.id}",
        payload=changes,
        ip=get_client_ip(request),
    )
    await db.commit()
    return await _reseller_out(db, reseller)


@router.post(
    "/{reseller_id}/block", summary="Bloqueia ou desbloqueia a revenda", response_model=ResellerOut
)
async def block_reseller(
    reseller_id: int, body: BlockUpdate, admin: CurrentAdmin, db: DbSession, request: Request
) -> ResellerOut:
    reseller = await _get_reseller(db, reseller_id)
    reseller.is_blocked = body.is_blocked
    await audit.record(
        db,
        actor_type="admin",
        actor_id=admin.id,
        action="reseller.block" if body.is_blocked else "reseller.unblock",
        target=f"reseller:{reseller.id}",
        ip=get_client_ip(request),
    )
    await db.commit()
    return await _reseller_out(db, reseller)


@router.post(
    "/{reseller_id}/password", summary="Redefine a senha da revenda", response_model=Message
)
async def reset_password(
    reseller_id: int, body: PasswordReset, admin: CurrentAdmin, db: DbSession, request: Request
) -> Message:
    reseller = await _get_reseller(db, reseller_id)
    reseller.password_hash = hash_password(body.password)
    await audit.record(
        db,
        actor_type="admin",
        actor_id=admin.id,
        action="reseller.password_reset",
        target=f"reseller:{reseller.id}",
        ip=get_client_ip(request),
    )
    await db.commit()
    return Message(message="Senha redefinida.")


@router.post(
    "/{reseller_id}/credits",
    summary="Ajuste manual de créditos (gera ledger + auditoria)",
    response_model=ResellerOut,
)
async def adjust_reseller_credits(
    reseller_id: int, body: CreditAdjust, admin: CurrentAdmin, db: DbSession, request: Request
) -> ResellerOut:
    if not await credits_enabled(db):
        raise bad_request(MSG_CREDITS_DISABLED, "credits_disabled")
    reseller = await _get_reseller(db, reseller_id)
    await adjust_credits(
        db,
        reseller,
        body.delta,
        reason="admin_adjustment",
        note=body.note,
        actor_type="admin",
        actor_id=admin.id,
        ip=get_client_ip(request),
    )
    await db.commit()
    return await _reseller_out(db, reseller)


@router.get(
    "/{reseller_id}/credits",
    summary="Histórico de movimentação de créditos",
    response_model=Page[LedgerOut],
)
async def list_reseller_credits(
    reseller_id: int, _: CurrentAdmin, db: DbSession, params: PageParams = Depends()
) -> Page[LedgerOut]:
    await _get_reseller(db, reseller_id)
    stmt = (
        select(CreditLedger)
        .where(CreditLedger.reseller_id == reseller_id)
        .order_by(CreditLedger.id.desc())
    )
    return await paginate(db, stmt, params, LedgerOut.model_validate)


@router.patch(
    "/{reseller_id}/expiration",
    summary="Define o vencimento da revenda",
    response_model=ResellerOut,
)
async def update_expiration(
    reseller_id: int,
    body: ExpirationUpdate,
    admin: CurrentAdmin,
    db: DbSession,
    request: Request,
) -> ResellerOut:
    reseller = await _get_reseller(db, reseller_id)
    previous = reseller.expires_at
    reseller.expires_at = body.expires_at
    await audit.record(
        db,
        actor_type="admin",
        actor_id=admin.id,
        action="reseller.expiration",
        target=f"reseller:{reseller.id}",
        payload={"from": str(previous), "to": str(body.expires_at)},
        ip=get_client_ip(request),
    )
    await db.commit()
    return await _reseller_out(db, reseller)


@router.delete("/{reseller_id}", summary="Exclui a revenda", response_model=Message)
async def delete_reseller(
    reseller_id: int, admin: CurrentAdmin, db: DbSession, request: Request
) -> Message:
    reseller = await _get_reseller(db, reseller_id)
    await audit.record(
        db,
        actor_type="admin",
        actor_id=admin.id,
        action="reseller.delete",
        target=f"reseller:{reseller.id}",
        payload={"username": reseller.username, "credits": reseller.credits},
        ip=get_client_ip(request),
    )
    await db.delete(reseller)
    await db.commit()
    return Message(message="Revenda excluída.")


@router.get(
    "/{reseller_id}/devices",
    summary="Dispositivos da revenda (somente leitura)",
    response_model=Page[ResellerDeviceOut],
)
async def list_reseller_devices(
    reseller_id: int, _: CurrentAdmin, db: DbSession, params: PageParams = Depends()
) -> Page[ResellerDeviceOut]:
    await _get_reseller(db, reseller_id)
    stmt = (
        select(Device)
        .where(Device.reseller_id == reseller_id)
        .options(selectinload(Device.playlists))
        .order_by(Device.created_at.desc(), Device.id.desc())
    )
    if params.search:
        term = f"%{params.search.strip()}%"
        stmt = stmt.where(or_(Device.mac_address.ilike(term), Device.client_name.ilike(term)))

    def to_out(device: Device) -> ResellerDeviceOut:
        out = device_to_out(device)
        out.playlist_url = None  # credentials are never shown to the admin
        return out

    return await paginate(db, stmt, params, to_out)


@router.get(
    "/{reseller_id}/payments",
    summary="Histórico de pagamentos da revenda",
    response_model=Page[PaymentOut],
)
async def list_reseller_payments(
    reseller_id: int, _: CurrentAdmin, db: DbSession, params: PageParams = Depends()
) -> Page[PaymentOut]:
    reseller = await _get_reseller(db, reseller_id)
    stmt = select(Payment).where(Payment.reseller_id == reseller_id).order_by(Payment.id.desc())

    def to_out(payment: Payment) -> PaymentOut:
        out = PaymentOut.model_validate(payment)
        out.reseller_username = reseller.username
        return out

    return await paginate(db, stmt, params, to_out)
