from datetime import datetime
from decimal import Decimal

from fastapi import APIRouter, Depends, Request, status
from pydantic import BaseModel, Field, model_validator
from sqlalchemy import select

from app.core.deps import CurrentResellerAllowExpired, DbSession, get_client_ip
from app.core.exceptions import not_found
from app.core.pagination import paginate
from app.models import Payment
from app.schemas.admin import PaymentOut
from app.schemas.common import Page, PageParams
from app.services.billing import (
    MAX_MONTHS,
    create_pix_payment,
    extended_expiration,
    plans_from_settings,
    sync_payment,
)
from app.services.settings import get_all_settings

router = APIRouter(prefix="/billing", tags=["reseller: billing"])


class PlanOut(BaseModel):
    id: int
    months: int
    price: Decimal


class PlansOut(BaseModel):
    monthly_price: Decimal
    max_months: int
    packages: list[PlanOut]
    can_renew: bool
    expires_at: str | None


class PixCreate(BaseModel):
    months: int | None = Field(None, ge=1, le=MAX_MONTHS)
    package_id: int | None = Field(None, ge=1)

    @model_validator(mode="after")
    def _one_of(self) -> "PixCreate":
        if (self.months is None) == (self.package_id is None):
            raise ValueError("informe months ou package_id")
        return self


class PixOut(BaseModel):
    payment_id: int
    status: str
    months: int
    amount: Decimal
    qr_code: str | None
    qr_base64: str | None
    expires_at: datetime | None
    paid_at: datetime | None
    new_expires_at: str | None
    projected_expires_at: str


def _pix_out(payment: Payment) -> PixOut:
    return PixOut(
        payment_id=payment.id,
        status=payment.status,
        months=payment.months,
        amount=payment.amount,
        qr_code=payment.qr_code,
        qr_base64=payment.qr_base64,
        expires_at=payment.expires_at,
        paid_at=payment.paid_at,
        new_expires_at=str(payment.new_expires_at) if payment.new_expires_at else None,
        projected_expires_at=str(extended_expiration(payment.previous_expires_at, payment.months)),
    )


@router.get("/plans", summary="Preço mensal e pacotes promocionais", response_model=PlansOut)
async def plans(reseller: CurrentResellerAllowExpired, db: DbSession) -> PlansOut:
    monthly, packages = plans_from_settings(await get_all_settings(db))
    return PlansOut(
        monthly_price=monthly,
        max_months=MAX_MONTHS,
        packages=[PlanOut(id=p.id, months=p.months, price=p.price) for p in packages],
        can_renew=reseller.expires_at is not None,
        expires_at=str(reseller.expires_at) if reseller.expires_at else None,
    )


@router.post(
    "/pix",
    summary="Gera cobrança Pix para renovar a revenda",
    response_model=PixOut,
    status_code=status.HTTP_201_CREATED,
)
async def create_pix(
    body: PixCreate, reseller: CurrentResellerAllowExpired, db: DbSession, request: Request
) -> PixOut:
    payment = await create_pix_payment(
        db, reseller, months=body.months, package_id=body.package_id, ip=get_client_ip(request)
    )
    await db.commit()
    return _pix_out(payment)


@router.get("/pix/{payment_id}", summary="Status da cobrança (polling)", response_model=PixOut)
async def get_pix(payment_id: int, reseller: CurrentResellerAllowExpired, db: DbSession) -> PixOut:
    payment = await db.scalar(
        select(Payment).where(Payment.id == payment_id, Payment.reseller_id == reseller.id)
    )
    if payment is None:
        raise not_found("Cobrança não encontrada.")
    payment = await sync_payment(db, payment, source="polling")
    await db.commit()
    return _pix_out(payment)


@router.get(
    "/history", summary="Histórico de pagamentos da revenda", response_model=Page[PaymentOut]
)
async def history(
    reseller: CurrentResellerAllowExpired, db: DbSession, params: PageParams = Depends()
) -> Page[PaymentOut]:
    stmt = select(Payment).where(Payment.reseller_id == reseller.id).order_by(Payment.id.desc())
    return await paginate(db, stmt, params, PaymentOut.model_validate)
