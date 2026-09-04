"""Reseller renewal via Pix: price calculation (server side only), charge creation,
status synchronisation and expiration extension."""

import calendar
import logging
from dataclasses import dataclass
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal
from typing import Any
from urllib.parse import urlsplit

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.exceptions import ApiError, bad_request
from app.models import Payment, Reseller
from app.services import audit
from app.services.payments import get_provider
from app.services.payments.base import ProviderError
from app.services.settings import get_all_settings

log = logging.getLogger(__name__)

MAX_MONTHS = 60
MSG_NO_EXPIRATION = (
    "Sua revenda não possui vencimento definido e não precisa de renovação. "
    "Fale com o administrador."
)
MSG_INVALID_PACKAGE = "Pacote inválido."
MSG_PROVIDER = "Não foi possível gerar o Pix. Tente novamente em instantes."


@dataclass(frozen=True)
class Plan:
    id: int
    months: int
    price: Decimal


def plans_from_settings(values: dict[str, Any]) -> tuple[Decimal, list[Plan]]:
    monthly = Decimal(str(values.get("monthly_price", "0")))
    packages = []
    for i, pkg in enumerate(values.get("packages") or [], start=1):
        packages.append(Plan(id=i, months=int(pkg["months"]), price=Decimal(str(pkg["price"]))))
    return monthly, packages


def compute_order(
    values: dict[str, Any], months: int | None, package_id: int | None
) -> tuple[int, Decimal]:
    """Returns (months, amount). Either a package or a number of months must be given."""
    monthly, packages = plans_from_settings(values)
    if package_id is not None:
        for plan in packages:
            if plan.id == package_id:
                return plan.months, plan.price
        raise bad_request(MSG_INVALID_PACKAGE, "invalid_package")
    if months is None or months < 1 or months > MAX_MONTHS:
        raise bad_request(f"Informe de 1 a {MAX_MONTHS} meses.", "invalid_months")
    if monthly <= 0:
        raise bad_request("Preço mensal não configurado.", "price_not_set")
    return months, (monthly * months).quantize(Decimal("0.01"))


def add_months(start: date, months: int) -> date:
    month_index = start.month - 1 + months
    year = start.year + month_index // 12
    month = month_index % 12 + 1
    day = min(start.day, calendar.monthrange(year, month)[1])
    return date(year, month, day)


def extended_expiration(current: date | None, months: int, today: date | None = None) -> date:
    today = today or date.today()
    base = current if current is not None and current > today else today
    return add_months(base, months)


def _payer_email(reseller: Reseller) -> str:
    host = urlsplit(get_settings().public_base_url).hostname or "plataforma.local"
    return f"revenda-{reseller.id}@{host}"


async def create_pix_payment(
    db: AsyncSession,
    reseller: Reseller,
    *,
    months: int | None,
    package_id: int | None,
    ip: str | None,
) -> Payment:
    if reseller.expires_at is None:
        raise ApiError(422, MSG_NO_EXPIRATION, "no_expiration")
    values = await get_all_settings(db)
    months_total, amount = compute_order(values, months, package_id)
    settings = get_settings()
    expires_at = datetime.now(UTC) + timedelta(minutes=settings.pix_expiration_minutes)

    payment = Payment(
        reseller_id=reseller.id,
        provider=get_provider().name,
        months=months_total,
        amount=amount,
        status="pending",
        previous_expires_at=reseller.expires_at,
        expires_at=expires_at,
    )
    db.add(payment)
    await db.flush()

    try:
        charge = await get_provider().create_pix(
            amount=amount,
            description=f"Renovação {months_total} mês(es) - {values.get('platform_name', '')}",
            external_reference=f"payment:{payment.id}",
            payer_email=_payer_email(reseller),
            expires_at=expires_at,
        )
    except ProviderError as exc:
        log.warning("pix creation failed for reseller %s: %s", reseller.id, exc)
        await db.delete(payment)
        await db.flush()
        raise ApiError(502, MSG_PROVIDER, "provider_error") from exc

    payment.provider_id = charge.provider_id
    payment.qr_code = charge.qr_code
    payment.qr_base64 = charge.qr_base64
    payment.expires_at = charge.expires_at
    await db.flush()
    await audit.record(
        db,
        actor_type="reseller",
        actor_id=reseller.id,
        action="payment.create",
        target=f"payment:{payment.id}",
        payload={"months": months_total, "amount": str(amount), "provider_id": payment.provider_id},
        ip=ip,
    )
    return payment


async def approve_payment(
    db: AsyncSession, payment: Payment, paid_at: datetime | None, *, source: str
) -> bool:
    """Marks the payment approved and extends the reseller expiration. Idempotent."""
    if payment.status == "approved":
        return False
    reseller = await db.get(Reseller, payment.reseller_id) if payment.reseller_id else None
    payment.status = "approved"
    payment.paid_at = paid_at or datetime.now(UTC)
    if reseller is not None:
        payment.previous_expires_at = reseller.expires_at
        reseller.expires_at = extended_expiration(reseller.expires_at, payment.months)
        payment.new_expires_at = reseller.expires_at
    await db.flush()
    await audit.record(
        db,
        actor_type="system",
        actor_id=None,
        action="payment.approved",
        target=f"payment:{payment.id}",
        payload={
            "reseller_id": payment.reseller_id,
            "months": payment.months,
            "amount": str(payment.amount),
            "provider_id": payment.provider_id,
            "previous_expires_at": str(payment.previous_expires_at),
            "new_expires_at": str(payment.new_expires_at),
            "source": source,
        },
    )
    return True


async def sync_payment(db: AsyncSession, payment: Payment, *, source: str) -> Payment:
    """Fetches the provider status and applies it to a pending payment."""
    if payment.status != "pending" or not payment.provider_id:
        return payment
    try:
        remote = await get_provider().get_payment(payment.provider_id)
    except ProviderError as exc:
        log.warning("payment sync failed for %s: %s", payment.id, exc)
        return payment
    if remote.status == "approved":
        await approve_payment(db, payment, remote.paid_at, source=source)
    elif remote.status in ("cancelled", "expired"):
        payment.status = remote.status
        await db.flush()
    elif payment.expires_at and payment.expires_at < datetime.now(UTC):
        payment.status = "expired"
        await db.flush()
    return payment


async def find_by_provider_id(db: AsyncSession, provider: str, provider_id: str) -> Payment | None:
    return await db.scalar(
        select(Payment).where(Payment.provider == provider, Payment.provider_id == provider_id)
    )
