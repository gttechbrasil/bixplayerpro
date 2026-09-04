from datetime import UTC, date, datetime, timedelta
from decimal import Decimal

from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AuditLog, Payment, Reseller, Setting
from app.services.billing import add_months, extended_expiration
from app.services.payments.fake import FakeProvider
from tests.conftest import RESELLER_PASSWORD, login

BILLING = "/api/v1/reseller/billing"


def test_add_months_and_extension() -> None:
    assert add_months(date(2026, 1, 31), 1) == date(2026, 2, 28)
    assert add_months(date(2026, 11, 15), 3) == date(2027, 2, 15)
    today = date(2026, 9, 4)
    # future expiration: extends from it
    assert extended_expiration(date(2026, 9, 20), 1, today) == date(2026, 10, 20)
    # past or missing expiration: extends from today
    assert extended_expiration(date(2026, 8, 1), 2, today) == date(2026, 11, 4)
    assert extended_expiration(None, 12, today) == date(2027, 9, 4)


async def test_plans(reseller_client: AsyncClient, db: AsyncSession) -> None:
    db.add(Setting(key="packages", value=[{"months": 3, "price": "90.00"}]))
    await db.flush()
    resp = await reseller_client.get(f"{BILLING}/plans")
    assert resp.status_code == 200
    body = resp.json()
    assert Decimal(body["monthly_price"]) == Decimal("35.00")
    assert body["max_months"] == 60
    assert body["packages"] == [{"id": 1, "months": 3, "price": "90.00"}]
    assert body["can_renew"] is True


async def test_pix_flow_with_polling(
    reseller_client: AsyncClient,
    reseller_user: Reseller,
    db: AsyncSession,
    fake_provider: FakeProvider,
) -> None:
    original = reseller_user.expires_at
    assert original is not None and original > date.today()

    resp = await reseller_client.post(f"{BILLING}/pix", json={"months": 3})
    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert body["status"] == "pending"
    assert Decimal(body["amount"]) == Decimal("105.00")
    assert body["months"] == 3
    assert body["qr_code"].startswith("00020126") and body["qr_base64"]
    assert body["expires_at"]
    assert body["projected_expires_at"] == str(add_months(original, 3))
    pid = body["payment_id"]

    payment = await db.get(Payment, pid)
    assert payment is not None and payment.provider == "fake" and payment.provider_id
    assert payment.status == "pending"

    # still pending
    resp = await reseller_client.get(f"{BILLING}/pix/{pid}")
    assert resp.json()["status"] == "pending"

    fake_provider.approve(payment.provider_id)
    resp = await reseller_client.get(f"{BILLING}/pix/{pid}")
    assert resp.status_code == 200
    assert resp.json()["status"] == "approved"
    assert resp.json()["new_expires_at"] == str(add_months(original, 3))

    await db.refresh(reseller_user)
    assert reseller_user.expires_at == add_months(original, 3)
    await db.refresh(payment)
    assert payment.paid_at is not None
    assert payment.previous_expires_at == original

    # polling again must not extend twice
    resp = await reseller_client.get(f"{BILLING}/pix/{pid}")
    assert resp.json()["status"] == "approved"
    await db.refresh(reseller_user)
    assert reseller_user.expires_at == add_months(original, 3)

    actions = (await db.scalars(select(AuditLog.action).order_by(AuditLog.id))).all()
    assert actions == ["payment.create", "payment.approved"]
    approved = await db.scalar(select(AuditLog).where(AuditLog.action == "payment.approved"))
    assert approved is not None and approved.payload["source"] == "polling"

    history = await reseller_client.get(f"{BILLING}/history")
    assert history.json()["total"] == 1 and history.json()["items"][0]["status"] == "approved"


async def test_pix_with_package_and_validation(
    reseller_client: AsyncClient, db: AsyncSession, fake_provider: FakeProvider
) -> None:
    db.add(Setting(key="packages", value=[{"months": 12, "price": "350.00"}]))
    await db.flush()
    resp = await reseller_client.post(f"{BILLING}/pix", json={"package_id": 1})
    assert resp.status_code == 201, resp.text
    assert Decimal(resp.json()["amount"]) == Decimal("350.00") and resp.json()["months"] == 12

    assert (await reseller_client.post(f"{BILLING}/pix", json={"package_id": 9})).status_code == 400
    assert (await reseller_client.post(f"{BILLING}/pix", json={})).status_code == 422
    assert (
        await reseller_client.post(f"{BILLING}/pix", json={"months": 1, "package_id": 1})
    ).status_code == 422
    assert (await reseller_client.post(f"{BILLING}/pix", json={"months": 61})).status_code == 422
    assert (await reseller_client.get(f"{BILLING}/pix/999999")).status_code == 404


async def test_pix_refused_without_expiration(
    reseller_client: AsyncClient, reseller_user: Reseller, db: AsyncSession
) -> None:
    reseller_user.expires_at = None
    await db.flush()
    plans = await reseller_client.get(f"{BILLING}/plans")
    assert plans.json()["can_renew"] is False
    resp = await reseller_client.post(f"{BILLING}/pix", json={"months": 1})
    assert resp.status_code == 422
    assert resp.json()["detail"]["code"] == "no_expiration"
    assert "administrador" in resp.json()["detail"]["message"]
    assert (await db.scalars(select(Payment))).all() == []


async def test_expired_reseller_renews_from_today(
    client: AsyncClient, reseller_user: Reseller, db: AsyncSession, fake_provider: FakeProvider
) -> None:
    reseller_user.expires_at = date.today() - timedelta(days=10)
    await db.flush()
    await login(client, "/api/v1/auth/reseller/login", "revenda", RESELLER_PASSWORD)

    resp = await client.post(f"{BILLING}/pix", json={"months": 2})
    assert resp.status_code == 201, resp.text
    pid = resp.json()["payment_id"]
    assert resp.json()["projected_expires_at"] == str(add_months(date.today(), 2))
    payment = await db.get(Payment, pid)
    assert payment is not None
    fake_provider.approve(payment.provider_id)
    resp = await client.get(f"{BILLING}/pix/{pid}")
    assert resp.json()["status"] == "approved"
    await db.refresh(reseller_user)
    assert reseller_user.expires_at == add_months(date.today(), 2)
    # the rest of the panel opens again
    assert (await client.get("/api/v1/reseller/devices")).status_code == 200


async def test_pix_expires_and_cancels(
    reseller_client: AsyncClient, db: AsyncSession, fake_provider: FakeProvider
) -> None:
    resp = await reseller_client.post(f"{BILLING}/pix", json={"months": 1})
    pid = resp.json()["payment_id"]
    payment = await db.get(Payment, pid)
    assert payment is not None
    payment.expires_at = datetime.now(UTC) - timedelta(minutes=1)
    await db.flush()
    resp = await reseller_client.get(f"{BILLING}/pix/{pid}")
    assert resp.json()["status"] == "expired"

    resp = await reseller_client.post(f"{BILLING}/pix", json={"months": 1})
    pid2 = resp.json()["payment_id"]
    payment2 = await db.get(Payment, pid2)
    assert payment2 is not None
    fake_provider.set_status(payment2.provider_id, "cancelled")
    resp = await reseller_client.get(f"{BILLING}/pix/{pid2}")
    assert resp.json()["status"] == "cancelled"


async def test_provider_failure_leaves_no_payment(
    reseller_client: AsyncClient, db: AsyncSession, fake_provider: FakeProvider, monkeypatch
) -> None:
    from app.services.payments.base import ProviderError

    async def boom(**kwargs):
        raise ProviderError("down")

    monkeypatch.setattr(fake_provider, "create_pix", boom)
    resp = await reseller_client.post(f"{BILLING}/pix", json={"months": 1})
    assert resp.status_code == 502
    assert resp.json()["detail"]["code"] == "provider_error"
    assert (await db.scalars(select(Payment))).all() == []
