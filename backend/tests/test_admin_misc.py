from datetime import UTC, datetime, timedelta
from decimal import Decimal

from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Device, Payment, Reseller

SETTINGS = "/api/v1/admin/settings"


async def test_settings_get_and_update(admin_client: AsyncClient, client: AsyncClient) -> None:
    resp = await admin_client.get(SETTINGS)
    assert resp.status_code == 200
    assert Decimal(resp.json()["monthly_price"]) == Decimal("35.00")
    assert resp.json()["packages"] == []

    resp = await admin_client.put(
        SETTINGS,
        json={
            "monthly_price": "40.00",
            "packages": [{"months": 3, "price": "100.00"}, {"months": 12, "price": "350"}],
            "min_app_version": "1.2.0",
            "apk_url": "https://cdn/app.apk",
        },
    )
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert Decimal(body["monthly_price"]) == Decimal("40.00")
    assert body["packages"][1] == {"months": 12, "price": "350.00"}
    assert body["min_app_version"] == "1.2.0"

    resp = await admin_client.put(SETTINGS, json={"min_app_version": "abc"})
    assert resp.status_code == 422
    resp = await admin_client.put(SETTINGS, json={"monthly_price": "-1"})
    assert resp.status_code == 422

    # the device config must reflect the new values
    reg = await client.post("/api/v1/device/register", json={"device_id": "dev-settings"})
    cfg = await client.get(
        "/api/v1/device/config", headers={"Authorization": f"Bearer {reg.json()['token']}"}
    )
    assert cfg.json()["min_app_version"] == "1.2.0"
    assert cfg.json()["apk_url"] == "https://cdn/app.apk"


async def test_dashboard(
    admin_client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    blocked = Reseller(username="b", name="B", password_hash="x", is_blocked=True)
    db.add(blocked)
    await db.flush()
    now = datetime.now(UTC)
    db.add_all(
        [
            Device(mac_address="02:50:50:00:00:01"),
            Device(mac_address="02:50:50:00:00:02", reseller_id=reseller_user.id, last_seen_at=now),
            Device(
                mac_address="02:50:50:00:00:03",
                reseller_id=reseller_user.id,
                license_expires_at=now.date() - timedelta(days=1),
            ),
            Device(mac_address="02:50:50:00:00:04", reseller_id=blocked.id),
            Payment(
                reseller_id=reseller_user.id,
                provider="mercadopago",
                provider_id="p1",
                months=1,
                amount=Decimal("35.00"),
                status="approved",
                paid_at=now,
            ),
            Payment(
                reseller_id=reseller_user.id,
                provider="mercadopago",
                provider_id="p2",
                months=2,
                amount=Decimal("70.00"),
                status="pending",
            ),
        ]
    )
    await db.flush()

    resp = await admin_client.get("/api/v1/admin/dashboard")
    assert resp.status_code == 200
    body = resp.json()
    assert body["resellers_total"] == 2
    assert body["resellers_active"] == 1
    assert body["resellers_blocked"] == 1
    assert body["devices_total"] == 4
    assert body["devices_registered"] == 3
    assert body["devices_active"] == 1
    assert body["devices_seen_24h"] == 1
    assert body["payments_month_count"] == 1
    assert Decimal(body["payments_month_amount"]) == Decimal("35.00")
    assert body["payments_pending"] == 1


async def test_payments_listing(
    admin_client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    db.add_all(
        [
            Payment(
                reseller_id=reseller_user.id,
                provider="mercadopago",
                provider_id="a1",
                months=1,
                amount=35,
                status="approved",
            ),
            Payment(
                reseller_id=reseller_user.id,
                provider="mercadopago",
                provider_id="a2",
                months=3,
                amount=100,
                status="pending",
            ),
            Payment(
                reseller_id=None,
                provider="mercadopago",
                provider_id="a3",
                months=1,
                amount=35,
                status="cancelled",
            ),
        ]
    )
    await db.flush()
    resp = await admin_client.get("/api/v1/admin/payments")
    assert resp.status_code == 200
    assert resp.json()["total"] == 3
    assert resp.json()["items"][0]["reseller_username"] is None

    resp = await admin_client.get("/api/v1/admin/payments", params={"status": "pending"})
    assert [p["provider_id"] for p in resp.json()["items"]] == ["a2"]

    resp = await admin_client.get(
        "/api/v1/admin/payments", params={"reseller_id": reseller_user.id}
    )
    assert resp.json()["total"] == 2
    resp = await admin_client.get("/api/v1/admin/payments", params={"search": "revenda"})
    assert resp.json()["total"] == 2
    today = datetime.now(UTC).date()
    resp = await admin_client.get(
        "/api/v1/admin/payments", params={"from": str(today), "to": str(today)}
    )
    assert resp.json()["total"] == 3
    resp = await admin_client.get(
        "/api/v1/admin/payments", params={"from": str(today + timedelta(1))}
    )
    assert resp.json()["total"] == 0


async def test_audit_log_listing(admin_client: AsyncClient) -> None:
    r = await admin_client.post(
        "/api/v1/admin/resellers", json={"username": "aud", "name": "A", "password": "senha123"}
    )
    rid = r.json()["id"]
    await admin_client.post(
        f"/api/v1/admin/resellers/{rid}/credits", json={"delta": 2, "note": "teste"}
    )

    resp = await admin_client.get("/api/v1/admin/audit-log")
    assert resp.status_code == 200
    items = resp.json()["items"]
    assert [i["action"] for i in items] == ["credits.adjust", "reseller.create"]
    assert items[0]["actor_type"] == "admin"

    resp = await admin_client.get("/api/v1/admin/audit-log", params={"action": "credits"})
    assert resp.json()["total"] == 1
    resp = await admin_client.get("/api/v1/admin/audit-log", params={"target": f"reseller:{rid}"})
    assert resp.json()["total"] == 2
    resp = await admin_client.get("/api/v1/admin/audit-log", params={"actor_type": "device"})
    assert resp.json()["total"] == 0
    resp = await admin_client.get("/api/v1/admin/audit-log", params={"search": "reseller.cre"})
    assert resp.json()["total"] == 1
    today = datetime.now(UTC).date()
    resp = await admin_client.get(
        "/api/v1/admin/audit-log", params={"from": str(today), "to": str(today), "actor_id": 1}
    )
    assert resp.status_code == 200
