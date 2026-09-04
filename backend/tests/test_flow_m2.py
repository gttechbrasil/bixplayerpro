"""End-to-end reseller flow (M2 §7): login -> register device -> app config returns the
playlist -> DNS migration -> config reflects the new host -> Pix -> webhook approves ->
expiration extended."""

import hashlib
import hmac
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal

import httpx
from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AuditLog, Payment, Reseller
from app.services.billing import add_months
from app.services.payments.mercadopago import MercadoPagoProvider
from tests.conftest import RESELLER_PASSWORD, login
from tests.test_mercadopago import mp_payment

SECRET = "flow-secret"


async def test_full_reseller_flow(
    client: AsyncClient, reseller_user: Reseller, db: AsyncSession, monkeypatch
) -> None:
    # --- the app registers first and shows its MAC ------------------------------
    reg = await client.post(
        "/api/v1/device/register",
        json={"device_id": "android-flow", "app_type": "tv", "app_version": "1.0.0"},
    )
    assert reg.status_code == 200
    mac, token = reg.json()["mac_address"], reg.json()["token"]
    bearer = {"Authorization": f"Bearer {token}"}
    assert (await client.get("/api/v1/device/config", headers=bearer)).json()["status"] == (
        "unregistered"
    )

    # --- reseller logs in and registers the MAC ----------------------------------
    await login(client, "/api/v1/auth/reseller/login", "revenda", RESELLER_PASSWORD)
    created = await client.post(
        "/api/v1/reseller/devices",
        json={
            "mac_address": mac,
            "client_name": "Cliente Fluxo",
            "playlist_name": "Principal",
            "playlist_url": "http://antigo.tv:8080/get.php?username=u&password=p&type=m3u_plus",
        },
    )
    assert created.status_code == 201, created.text

    cfg = (await client.get("/api/v1/device/config", headers=bearer)).json()
    assert cfg["status"] == "active" and cfg["registered"] is True
    assert cfg["client_name"] == "Cliente Fluxo"
    assert cfg["playlists"][0]["url"].startswith("http://antigo.tv:8080/get.php?username=u")
    assert "password=p" in cfg["playlists"][0]["url"]

    # --- DNS migration ------------------------------------------------------------
    hosts = (await client.get("/api/v1/reseller/dns")).json()
    assert hosts == [{"host": "http://antigo.tv:8080", "playlists": 1}]
    migrated = await client.post(
        "/api/v1/reseller/dns/migrate",
        json={"from_host": "http://antigo.tv:8080", "to_host": "https://novo.tv"},
    )
    assert migrated.status_code == 200 and migrated.json()["affected"] == 1

    cfg = (await client.get("/api/v1/device/config", headers=bearer)).json()
    assert cfg["playlists"][0]["url"].startswith("https://novo.tv/get.php?username=u")
    assert "password=p" in cfg["playlists"][0]["url"]

    # --- Pix through the Mercado Pago provider (HTTP mocked) -----------------------
    state = {"status": "pending"}

    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "POST":
            return httpx.Response(201, json=mp_payment(4242, "pending"))
        return httpx.Response(200, json=mp_payment(4242, state["status"]))

    provider = MercadoPagoProvider("TEST-x", SECRET, transport=httpx.MockTransport(handler))
    import app.api.v1.webhooks as webhooks_module
    import app.services.billing as billing_module

    monkeypatch.setattr(billing_module, "get_provider", lambda: provider)
    monkeypatch.setattr(webhooks_module, "get_provider", lambda: provider)

    original = reseller_user.expires_at
    assert original is not None
    pix = await client.post("/api/v1/reseller/billing/pix", json={"months": 3})
    assert pix.status_code == 201, pix.text
    assert pix.json()["status"] == "pending"
    assert Decimal(pix.json()["amount"]) == Decimal("105.00")
    assert pix.json()["qr_code"].startswith("00020126")
    payment_id = pix.json()["payment_id"]

    # polling while still pending
    poll = await client.get(f"/api/v1/reseller/billing/pix/{payment_id}")
    assert poll.json()["status"] == "pending"

    # --- webhook approves --------------------------------------------------------
    state["status"] = "approved"
    ts = str(int(datetime.now(UTC).timestamp()))
    manifest = f"id:4242;request-id:req-flow;ts:{ts};"
    v1 = hmac.new(SECRET.encode(), manifest.encode(), hashlib.sha256).hexdigest()
    hook = await client.post(
        "/api/v1/webhooks/mercadopago?data.id=4242&type=payment",
        json={"type": "payment", "action": "payment.updated", "data": {"id": "4242"}},
        headers={"x-signature": f"ts={ts},v1={v1}", "x-request-id": "req-flow"},
    )
    assert hook.status_code == 200 and hook.json() == {"processed": True, "status": "approved"}

    await db.refresh(reseller_user)
    assert reseller_user.expires_at == add_months(original, 3)
    payment = await db.get(Payment, payment_id)
    assert payment is not None and payment.status == "approved"
    assert payment.previous_expires_at == original
    assert payment.new_expires_at == reseller_user.expires_at

    me = await client.get("/api/v1/auth/me")
    assert me.json()["user"]["expires_at"] == str(add_months(original, 3))
    poll = await client.get(f"/api/v1/reseller/billing/pix/{payment_id}")
    assert poll.json()["status"] == "approved"
    assert poll.json()["new_expires_at"] == str(add_months(original, 3))

    history = await client.get("/api/v1/reseller/billing/history")
    assert history.json()["total"] == 1

    actions = (await db.scalars(select(AuditLog.action).order_by(AuditLog.id))).all()
    assert actions == [
        "device.create",
        "dns.migrate",
        "payment.create",
        "payment.approved",
    ]
    assert date.today() < reseller_user.expires_at
    assert reseller_user.expires_at - original == add_months(original, 3) - original
    assert (reseller_user.expires_at - original) > timedelta(days=80)
