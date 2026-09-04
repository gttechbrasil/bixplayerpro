from datetime import UTC, datetime, timedelta
from decimal import Decimal

from fastapi import APIRouter
from sqlalchemy import and_, func, or_, select

from app.core.deps import CurrentAdmin, DbSession
from app.models import Device, Payment, Reseller
from app.schemas.admin import DashboardOut

router = APIRouter(prefix="/dashboard", tags=["admin: dashboard"])


@router.get("", response_model=DashboardOut)
async def dashboard(_: CurrentAdmin, db: DbSession) -> DashboardOut:
    today = func.current_date()
    now = datetime.now(UTC)
    month_start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)

    reseller_expired = and_(Reseller.expires_at.is_not(None), Reseller.expires_at < today)
    reseller_active = and_(Reseller.is_blocked.is_(False), ~reseller_expired)

    resellers = (
        await db.execute(
            select(
                func.count(),
                func.count().filter(reseller_active),
                func.count().filter(Reseller.is_blocked.is_(True)),
                func.count().filter(reseller_expired),
            ).select_from(Reseller)
        )
    ).one()

    device_license_ok = or_(Device.license_expires_at.is_(None), Device.license_expires_at >= today)
    devices = (
        await db.execute(
            select(
                func.count(),
                func.count().filter(Device.reseller_id.is_not(None)),
                func.count().filter(
                    and_(Device.reseller_id.is_not(None), device_license_ok, reseller_active)
                ),
                func.count().filter(Device.last_seen_at >= now - timedelta(hours=24)),
            )
            .select_from(Device)
            .outerjoin(Reseller, Reseller.id == Device.reseller_id)
        )
    ).one()

    payments = (
        await db.execute(
            select(
                func.count().filter(
                    and_(Payment.status == "approved", Payment.paid_at >= month_start)
                ),
                func.coalesce(
                    func.sum(Payment.amount).filter(
                        and_(Payment.status == "approved", Payment.paid_at >= month_start)
                    ),
                    0,
                ),
                func.count().filter(Payment.status == "pending"),
            ).select_from(Payment)
        )
    ).one()

    return DashboardOut(
        resellers_total=resellers[0],
        resellers_active=resellers[1],
        resellers_blocked=resellers[2],
        resellers_expired=resellers[3],
        devices_total=devices[0],
        devices_registered=devices[1],
        devices_active=devices[2],
        devices_seen_24h=devices[3],
        payments_month_count=payments[0],
        payments_month_amount=Decimal(payments[1]),
        payments_pending=payments[2],
    )
