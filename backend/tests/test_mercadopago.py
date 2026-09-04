"""Mercado Pago provider (mocked HTTP) and webhook endpoint tests."""

import hashlib
import hmac
import json
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal

import httpx
import pytest
from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AuditLog, Payment, Reseller
from app.services.payments.base import ProviderError
from app.services.payments.mercadopago import MercadoPagoProvider

SECRET = "webhook-secret-123"
WEBHOOK = "/api/v1/webhooks/mercadopago"


def mp_payment(pid: int, status: str, **extra) -> dict:
    """Payment object as returned by GET /v1/payments/{id}."""
    return {
        "id": pid,
        "status": status,
        "status_detail": "accredited" if status == "approved" else "pending_waiting_transfer",
        "payment_method_id": "pix",
        "transaction_amount": 105,
        "date_created": "2026-09-04T12:00:00.000-04:00",
        "date_approved": "2026-09-04T12:05:00.000-04:00" if status == "approved" else None,
        "date_of_expiration": "2099-01-01T00:00:00.000-04:00",
        "external_reference": "payment:1",
        "point_of_interaction": {
            "transaction_data": {
                "qr_code": "00020126580014br.gov.bcb.pix0136abc-def5204000053039865406105.005802BR",
                "qr_code_base64": "iVBORw0KGgo=",
                "ticket_url": "https://www.mercadopago.com.br/payments/1/ticket",
            }
        },
        **extra,
    }


def signature(data_id: str, request_id: str, ts: str = "1700000000") -> str:
    manifest = f"id:{data_id.lower()};request-id:{request_id};ts:{ts};"
    v1 = hmac.new(SECRET.encode(), manifest.encode(), hashlib.sha256).hexdigest()
    return f"ts={ts},v1={v1}"


class Recorder:
    def __init__(self, handler):
        self.handler = handler
        self.requests: list[httpx.Request] = []

    def __call__(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        return self.handler(request)


def provider_with(handler) -> tuple[MercadoPagoProvider, Recorder]:
    recorder = Recorder(handler)
    provider = MercadoPagoProvider(
        access_token="TEST-token", webhook_secret=SECRET, transport=httpx.MockTransport(recorder)
    )
    return provider, recorder


async def test_create_pix_maps_response() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/v1/payments"
        assert request.headers["Authorization"] == "Bearer TEST-token"
        assert request.headers["X-Idempotency-Key"]
        body = json.loads(request.content)
        assert body["payment_method_id"] == "pix"
        assert body["transaction_amount"] == 105.0
        assert body["payer"]["email"] == "revenda-1@test"
        assert body["external_reference"] == "payment:1"
        return httpx.Response(201, json=mp_payment(555, "pending"))

    provider, _ = provider_with(handler)
    charge = await provider.create_pix(
        amount=Decimal("105.00"),
        description="Renovação",
        external_reference="payment:1",
        payer_email="revenda-1@test",
        expires_at=datetime.now(UTC) + timedelta(minutes=30),
    )
    assert charge.provider_id == "555"
    assert charge.qr_code.startswith("00020126")
    assert charge.qr_base64 == "iVBORw0KGgo="
    assert charge.status == "pending"
    assert charge.expires_at.year == 2099


async def test_create_pix_errors() -> None:
    provider, _ = provider_with(lambda r: httpx.Response(400, json={"message": "bad"}))
    with pytest.raises(ProviderError):
        await provider.create_pix(
            amount=Decimal("1"),
            description="x",
            external_reference="x",
            payer_email="a@b",
            expires_at=datetime.now(UTC),
        )

    def no_qr(request: httpx.Request) -> httpx.Response:
        data = mp_payment(1, "pending")
        data["point_of_interaction"] = {}
        return httpx.Response(201, json=data)

    provider, _ = provider_with(no_qr)
    with pytest.raises(ProviderError):
        await provider.create_pix(
            amount=Decimal("1"),
            description="x",
            external_reference="x",
            payer_email="a@b",
            expires_at=datetime.now(UTC),
        )

    def down(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("boom")

    provider, _ = provider_with(down)
    with pytest.raises(ProviderError):
        await provider.get_payment("1")
    with pytest.raises(ProviderError):
        await provider.create_pix(
            amount=Decimal("1"),
            description="x",
            external_reference="x",
            payer_email="a@b",
            expires_at=datetime.now(UTC),
        )

    provider, _ = provider_with(lambda r: httpx.Response(500))
    with pytest.raises(ProviderError):
        await provider.get_payment("1")

    with pytest.raises(ProviderError):
        MercadoPagoProvider(access_token="")


@pytest.mark.parametrize(
    ("mp_status", "expected"),
    [
        ("approved", "approved"),
        ("pending", "pending"),
        ("in_process", "pending"),
        ("rejected", "cancelled"),
        ("cancelled", "cancelled"),
        ("refunded", "cancelled"),
    ],
)
async def test_get_payment_status_mapping(mp_status: str, expected: str) -> None:
    provider, rec = provider_with(lambda r: httpx.Response(200, json=mp_payment(9, mp_status)))
    remote = await provider.get_payment("9")
    assert rec.requests[0].url.path == "/v1/payments/9"
    assert remote.status == expected
    assert remote.amount == Decimal("105")
    if expected == "approved":
        assert remote.paid_at is not None


async def test_get_payment_expired_and_missing() -> None:
    provider, _ = provider_with(
        lambda r: httpx.Response(
            200, json=mp_payment(9, "pending", date_of_expiration="2020-01-01T00:00:00.000-04:00")
        )
    )
    assert (await provider.get_payment("9")).status == "expired"
    provider, _ = provider_with(lambda r: httpx.Response(404, json={}))
    assert (await provider.get_payment("9")).status == "cancelled"


def test_verify_webhook_signature() -> None:
    provider, _ = provider_with(lambda r: httpx.Response(200))
    headers = {"x-signature": signature("123", "req-1"), "x-request-id": "req-1"}
    assert provider.verify_webhook(headers=headers, data_id="123")
    assert not provider.verify_webhook(headers=headers, data_id="124")
    assert not provider.verify_webhook(headers={"x-signature": "garbage"}, data_id="123")
    assert not provider.verify_webhook(headers={}, data_id="123")
    unsigned = MercadoPagoProvider(access_token="t")
    assert unsigned.verify_webhook(headers={}, data_id="123")


# ---- webhook endpoint ---------------------------------------------------------------
@pytest.fixture
def mp_provider(monkeypatch):
    state = {"status": "approved"}

    def handler(request: httpx.Request) -> httpx.Response:
        pid = int(request.url.path.rsplit("/", 1)[1])
        return httpx.Response(200, json=mp_payment(pid, state["status"]))

    provider, recorder = provider_with(handler)
    import app.api.v1.webhooks as webhooks_module
    import app.services.billing as billing_module

    monkeypatch.setattr(webhooks_module, "get_provider", lambda: provider)
    monkeypatch.setattr(billing_module, "get_provider", lambda: provider)
    provider.state = state  # type: ignore[attr-defined]
    provider.recorder = recorder  # type: ignore[attr-defined]
    return provider


async def pending_payment(db: AsyncSession, reseller: Reseller, provider_id: str) -> Payment:
    payment = Payment(
        reseller_id=reseller.id,
        provider="mercadopago",
        provider_id=provider_id,
        months=3,
        amount=Decimal("105.00"),
        status="pending",
        previous_expires_at=reseller.expires_at,
        expires_at=datetime.now(UTC) + timedelta(minutes=30),
    )
    db.add(payment)
    await db.flush()
    return payment


def webhook_request(data_id: str, request_id: str = "req-1") -> tuple[dict, dict]:
    body = {
        "action": "payment.updated",
        "api_version": "v1",
        "data": {"id": data_id},
        "date_created": "2026-09-04T15:05:00Z",
        "id": 123456789,
        "live_mode": False,
        "type": "payment",
        "user_id": "3666529922",
    }
    headers = {"x-signature": signature(data_id, request_id), "x-request-id": request_id}
    return body, headers


async def test_webhook_approves_and_is_idempotent(
    client: AsyncClient, reseller_user: Reseller, db: AsyncSession, mp_provider
) -> None:
    original = reseller_user.expires_at
    assert original is not None
    payment = await pending_payment(db, reseller_user, "777")
    body, headers = webhook_request("777")

    resp = await client.post(f"{WEBHOOK}?data.id=777&type=payment", json=body, headers=headers)
    assert resp.status_code == 200, resp.text
    assert resp.json() == {"processed": True, "status": "approved"}
    assert mp_provider.recorder.requests[-1].url.path == "/v1/payments/777"

    await db.refresh(reseller_user)
    expected = date(original.year, original.month, original.day)
    from app.services.billing import add_months

    assert reseller_user.expires_at == add_months(expected, 3)
    await db.refresh(payment)
    assert payment.status == "approved" and payment.paid_at is not None
    assert payment.new_expires_at == reseller_user.expires_at

    # Mercado Pago retries: same notification again must not extend twice
    resp = await client.post(f"{WEBHOOK}?data.id=777&type=payment", json=body, headers=headers)
    assert resp.json()["status"] == "approved"
    await db.refresh(reseller_user)
    assert reseller_user.expires_at == add_months(expected, 3)

    approvals = (
        await db.scalars(select(AuditLog).where(AuditLog.action == "payment.approved"))
    ).all()
    assert len(approvals) == 1 and approvals[0].payload["source"] == "webhook"


async def test_webhook_rejects_bad_signature_and_ignores_unknown(
    client: AsyncClient, reseller_user: Reseller, db: AsyncSession, mp_provider
) -> None:
    await pending_payment(db, reseller_user, "888")
    body, _ = webhook_request("888")
    resp = await client.post(
        f"{WEBHOOK}?data.id=888",
        json=body,
        headers={"x-signature": "ts=1,v1=deadbeef", "x-request-id": "req-1"},
    )
    assert resp.status_code == 403

    body, headers = webhook_request("999")
    resp = await client.post(f"{WEBHOOK}?data.id=999", json=body, headers=headers)
    assert resp.status_code == 200 and resp.json() == {"processed": False, "status": None}

    # other event types and empty bodies are acknowledged without processing
    resp = await client.post(f"{WEBHOOK}", json={"type": "merchant_order", "data": {"id": "1"}})
    assert resp.status_code == 200 and resp.json()["processed"] is False
    resp = await client.post(f"{WEBHOOK}", content=b"", headers={"content-type": "text/plain"})
    assert resp.status_code == 200 and resp.json()["processed"] is False


async def test_webhook_pending_keeps_payment_pending(
    client: AsyncClient, reseller_user: Reseller, db: AsyncSession, mp_provider
) -> None:
    mp_provider.state["status"] = "pending"
    payment = await pending_payment(db, reseller_user, "555")
    body, headers = webhook_request("555")
    resp = await client.post(f"{WEBHOOK}?data.id=555", json=body, headers=headers)
    assert resp.json() == {"processed": True, "status": "pending"}
    await db.refresh(payment)
    assert payment.status == "pending"
