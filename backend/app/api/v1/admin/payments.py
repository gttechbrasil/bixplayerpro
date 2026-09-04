from datetime import date
from typing import Literal

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select

from app.core.deps import CurrentAdmin, DbSession
from app.core.pagination import paginate
from app.models import Payment, Reseller
from app.schemas.admin import PaymentOut
from app.schemas.common import Page, PageParams

router = APIRouter(prefix="/payments", tags=["admin: payments"])

PaymentStatus = Literal["pending", "approved", "cancelled", "expired"]


def _to_out(row) -> PaymentOut:
    payment, username = row
    out = PaymentOut.model_validate(payment)
    out.reseller_username = username
    return out


@router.get("", summary="Lista pagamentos com filtros", response_model=Page[PaymentOut])
async def list_payments(
    _: CurrentAdmin,
    db: DbSession,
    params: PageParams = Depends(),
    status_filter: PaymentStatus | None = Query(None, alias="status"),
    reseller_id: int | None = None,
    date_from: date | None = Query(None, alias="from"),
    date_to: date | None = Query(None, alias="to"),
) -> Page[PaymentOut]:
    stmt = (
        select(Payment, Reseller.username)
        .outerjoin(Reseller, Reseller.id == Payment.reseller_id)
        .order_by(Payment.id.desc())
    )
    if status_filter:
        stmt = stmt.where(Payment.status == status_filter)
    if reseller_id is not None:
        stmt = stmt.where(Payment.reseller_id == reseller_id)
    if date_from:
        stmt = stmt.where(Payment.created_at >= date_from)
    if date_to:
        stmt = stmt.where(Payment.created_at < date_to.fromordinal(date_to.toordinal() + 1))
    if params.search:
        term = f"%{params.search.strip()}%"
        stmt = stmt.where(Reseller.username.ilike(term) | Payment.provider_id.ilike(term))
    return await paginate(db, stmt, params, _to_out)
