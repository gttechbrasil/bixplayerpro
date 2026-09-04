from datetime import date, timedelta

from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AuditLog, CreditLedger, Device, Reseller

BASE = "/api/v1/admin/resellers"


async def create(client: AsyncClient, username: str = "loja1", **extra) -> dict:
    payload = {"username": username, "name": "Loja 1", "password": "senha123", **extra}
    resp = await client.post(BASE, json=payload)
    assert resp.status_code == 201, resp.text
    return resp.json()


async def test_requires_admin(client: AsyncClient, reseller_client: AsyncClient) -> None:
    assert (await client.get(BASE)).status_code == 401
    # a reseller session must not reach admin routes
    assert (await reseller_client.get(BASE)).status_code == 401


async def test_csrf_required_on_mutations(admin_client: AsyncClient) -> None:
    del admin_client.headers["X-CSRF-Token"]
    resp = await admin_client.post(
        BASE, json={"username": "x1", "name": "X", "password": "senha123"}
    )
    assert resp.status_code == 403
    assert resp.json()["detail"]["code"] == "csrf"
    # safe methods still work without the header
    assert (await admin_client.get(BASE)).status_code == 200


async def test_create_list_get(
    admin_client: AsyncClient, db: AsyncSession, credits_on: None
) -> None:
    created = await create(admin_client, credits=3, expires_at="2030-01-01")
    assert created["credits"] == 3
    assert created["expires_at"] == "2030-01-01"
    assert created["devices_count"] == 0

    dup = await admin_client.post(
        BASE, json={"username": "loja1", "name": "Dup", "password": "senha123"}
    )
    assert dup.status_code == 409

    await create(admin_client, "loja2")
    resp = await admin_client.get(BASE, params={"search": "loja1"})
    body = resp.json()
    assert body["total"] == 1 and body["items"][0]["username"] == "loja1"

    resp = await admin_client.get(BASE, params={"per_page": 1, "page": 2})
    assert resp.json()["total"] == 2 and len(resp.json()["items"]) == 1

    resp = await admin_client.get(f"{BASE}/{created['id']}")
    assert resp.status_code == 200 and resp.json()["username"] == "loja1"
    assert (await admin_client.get(f"{BASE}/999999")).status_code == 404

    ledger = (await db.scalars(select(CreditLedger))).all()
    assert len(ledger) == 1 and ledger[0].delta == 3 and ledger[0].balance_after == 3
    actions = (await db.scalars(select(AuditLog.action).order_by(AuditLog.id))).all()
    assert actions[:2] == ["reseller.create", "credits.adjust"]


async def test_status_filters(admin_client: AsyncClient, db: AsyncSession) -> None:
    active = await create(admin_client, "ativa")
    blocked = await create(admin_client, "bloqueada")
    expired = await create(admin_client, "vencida", expires_at=str(date.today() - timedelta(1)))
    await admin_client.post(f"{BASE}/{blocked['id']}/block", json={"is_blocked": True})

    async def ids(status: str) -> set[int]:
        resp = await admin_client.get(BASE, params={"status": status})
        return {i["id"] for i in resp.json()["items"]}

    assert await ids("active") == {active["id"]}
    assert await ids("blocked") == {blocked["id"]}
    assert await ids("expired") == {expired["id"]}


async def test_update_block_password(
    admin_client: AsyncClient, client: AsyncClient, db: AsyncSession
) -> None:
    created = await create(admin_client)
    rid = created["id"]

    resp = await admin_client.patch(f"{BASE}/{rid}", json={"name": "Nova", "theme": "grid"})
    assert resp.status_code == 200
    assert resp.json()["name"] == "Nova" and resp.json()["theme"] == "grid"
    assert (await admin_client.patch(f"{BASE}/{rid}", json={"theme": "theme_x"})).status_code == 422

    resp = await admin_client.post(f"{BASE}/{rid}/block", json={"is_blocked": True})
    assert resp.json()["is_blocked"] is True

    resp = await admin_client.post(f"{BASE}/{rid}/password", json={"password": "novasenha"})
    assert resp.status_code == 200
    reseller = await db.get(Reseller, rid)
    assert reseller is not None
    reseller.is_blocked = False
    await db.flush()
    login = await client.post(
        "/api/v1/auth/reseller/login", json={"username": "loja1", "password": "novasenha"}
    )
    assert login.status_code == 200


async def test_credits_adjust_and_history(
    admin_client: AsyncClient, db: AsyncSession, credits_on: None
) -> None:
    rid = (await create(admin_client))["id"]
    resp = await admin_client.post(
        f"{BASE}/{rid}/credits", json={"delta": 10, "note": "Compra de pacote"}
    )
    assert resp.status_code == 200 and resp.json()["credits"] == 10

    resp = await admin_client.post(f"{BASE}/{rid}/credits", json={"delta": -4, "note": "Ajuste"})
    assert resp.json()["credits"] == 6

    resp = await admin_client.post(f"{BASE}/{rid}/credits", json={"delta": -7, "note": "Demais"})
    assert resp.status_code == 400
    assert resp.json()["detail"]["code"] == "insufficient_credits"

    resp = await admin_client.post(f"{BASE}/{rid}/credits", json={"delta": 0, "note": "Nada"})
    assert resp.status_code == 400

    resp = await admin_client.post(f"{BASE}/{rid}/credits", json={"delta": 1, "note": "x"})
    assert resp.status_code == 422  # note too short

    history = await admin_client.get(f"{BASE}/{rid}/credits")
    items = history.json()["items"]
    assert [i["delta"] for i in items] == [-4, 10]
    assert items[0]["balance_after"] == 6
    assert (await db.get(Reseller, rid)).credits == 6

    audit = (await db.scalars(select(AuditLog).where(AuditLog.action == "credits.adjust"))).all()
    assert len(audit) == 2
    assert audit[0].payload["note"] == "Compra de pacote"


async def test_expiration_and_delete(admin_client: AsyncClient, db: AsyncSession) -> None:
    rid = (await create(admin_client))["id"]
    resp = await admin_client.patch(f"{BASE}/{rid}/expiration", json={"expires_at": "2031-05-10"})
    assert resp.json()["expires_at"] == "2031-05-10"
    resp = await admin_client.patch(f"{BASE}/{rid}/expiration", json={"expires_at": None})
    assert resp.json()["expires_at"] is None

    db.add(Device(reseller_id=rid, mac_address="02:50:50:AA:BB:CC"))
    await db.flush()
    assert (await admin_client.get(f"{BASE}/{rid}")).json()["devices_count"] == 1

    resp = await admin_client.delete(f"{BASE}/{rid}")
    assert resp.status_code == 200
    assert (await admin_client.get(f"{BASE}/{rid}")).status_code == 404
    device = await db.scalar(select(Device).where(Device.mac_address == "02:50:50:AA:BB:CC"))
    assert device is not None and device.reseller_id is None

    actions = (await db.scalars(select(AuditLog.action).order_by(AuditLog.id))).all()
    assert "reseller.expiration" in actions and actions[-1] == "reseller.delete"


async def test_credits_disabled_by_default(admin_client: AsyncClient, db: AsyncSession) -> None:
    created = await create(admin_client, credits=5)
    assert created["credits"] == 0  # initial credits ignored while disabled
    resp = await admin_client.post(
        f"{BASE}/{created['id']}/credits", json={"delta": 3, "note": "tentativa"}
    )
    assert resp.status_code == 400
    assert resp.json()["detail"]["code"] == "credits_disabled"
    assert (await db.scalars(select(CreditLedger))).all() == []
