from datetime import date, timedelta

import pytest
from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import hash_password
from app.models import AuditLog, CreditLedger, Device, Playlist, Reseller
from tests.test_device import CONFIG, XTREAM, bearer, register

BASE = "/api/v1/reseller/devices"


def payload(mac: str = "AA:BB:CC:DD:EE:01", **extra) -> dict:
    return {
        "mac_address": mac,
        "client_name": "Cliente 1",
        "playlist_name": "Lista principal",
        "playlist_url": XTREAM,
        **extra,
    }


async def test_requires_reseller_session(client: AsyncClient, admin_client: AsyncClient) -> None:
    assert (await client.get(BASE)).status_code == 401
    assert (await admin_client.get(BASE)).status_code == 401


async def test_create_manual_device(reseller_client: AsyncClient, db: AsyncSession) -> None:
    resp = await reseller_client.post(BASE, json=payload("aa-bb-cc-dd-ee-01"))
    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert body["mac_address"] == "AA:BB:CC:DD:EE:01"
    assert body["client_name"] == "Cliente 1"
    assert body["license_expires_at"] == "2050-01-01"
    assert body["playlist_name"] == "Lista principal"
    assert "password=p1" in body["playlist_url"]
    assert body["playlist_host"] == "http://cnplay.click"
    assert body["connected"] is False
    assert body["status"] == "active"

    playlist = await db.scalar(select(Playlist))
    assert playlist is not None and "p1" not in playlist.url and playlist.password_enc

    actions = (await db.scalars(select(AuditLog.action))).all()
    assert actions == ["device.create"]
    assert (await db.scalars(select(CreditLedger))).all() == []  # credits disabled by default


@pytest.mark.parametrize("mac", ["AA:BB", "ZZ:ZZ:ZZ:ZZ:ZZ:ZZ", "AABBCCDDEEFF00"])
async def test_invalid_mac(reseller_client: AsyncClient, mac: str) -> None:
    resp = await reseller_client.post(BASE, json=payload(mac))
    assert resp.status_code in (400, 422)


async def test_invalid_playlist_url(reseller_client: AsyncClient) -> None:
    resp = await reseller_client.post(BASE, json=payload(playlist_url="lista.m3u"))
    assert resp.status_code == 400
    assert resp.json()["detail"]["code"] == "invalid_playlist_url"


async def test_claim_device_registered_by_app(
    reseller_client: AsyncClient, client: AsyncClient, db: AsyncSession
) -> None:
    reg = await register(client, "android-xyz")
    cfg = (await client.get(CONFIG, headers=bearer(reg["token"]))).json()
    assert cfg["status"] == "unregistered"

    resp = await reseller_client.post(BASE, json=payload(reg["mac_address"].lower()))
    assert resp.status_code == 201, resp.text
    assert resp.json()["connected"] is True

    cfg = (await client.get(CONFIG, headers=bearer(reg["token"]))).json()
    assert cfg["status"] == "active"
    assert cfg["registered"] is True
    assert cfg["client_name"] == "Cliente 1"
    assert [p["name"] for p in cfg["playlists"]] == ["Lista principal"]
    assert "password=p1" in cfg["playlists"][0]["url"]

    audit = await db.scalar(select(AuditLog).where(AuditLog.action == "device.create"))
    assert audit is not None and audit.payload["claimed"] is True


async def test_mac_conflicts(reseller_client: AsyncClient, db: AsyncSession) -> None:
    assert (await reseller_client.post(BASE, json=payload())).status_code == 201
    dup = await reseller_client.post(BASE, json=payload())
    assert dup.status_code == 409
    assert dup.json()["detail"]["code"] == "mac_already_yours"

    other = Reseller(username="outra", name="Outra", password_hash=hash_password("x"))
    db.add(other)
    await db.flush()
    db.add(Device(mac_address="AA:BB:CC:DD:EE:02", reseller_id=other.id))
    await db.flush()
    resp = await reseller_client.post(BASE, json=payload("AA:BB:CC:DD:EE:02"))
    assert resp.status_code == 409
    assert resp.json()["detail"]["code"] == "mac_taken"
    assert "outro revendedor" in resp.json()["detail"]["message"]


async def test_credits_consumed_when_enabled(
    reseller_client: AsyncClient, reseller_user: Reseller, db: AsyncSession, credits_on: None
) -> None:
    reseller_user.credits = 1
    await db.flush()
    resp = await reseller_client.post(BASE, json=payload("AA:BB:CC:DD:EE:11"))
    assert resp.status_code == 201
    await db.refresh(reseller_user)
    assert reseller_user.credits == 0
    ledger = (await db.scalars(select(CreditLedger))).all()
    assert len(ledger) == 1 and ledger[0].delta == -1 and ledger[0].reason == "device_registration"

    resp = await reseller_client.post(BASE, json=payload("AA:BB:CC:DD:EE:12"))
    assert resp.status_code == 400
    assert resp.json()["detail"]["code"] == "insufficient_credits"
    assert (
        await db.scalar(select(Device).where(Device.mac_address == "AA:BB:CC:DD:EE:12"))
    ) is None


async def test_no_credit_needed_when_disabled(
    reseller_client: AsyncClient, reseller_user: Reseller, db: AsyncSession
) -> None:
    reseller_user.credits = 0
    await db.flush()
    resp = await reseller_client.post(BASE, json=payload("AA:BB:CC:DD:EE:21"))
    assert resp.status_code == 201
    await db.refresh(reseller_user)
    assert reseller_user.credits == 0


async def test_get_update_delete(reseller_client: AsyncClient, db: AsyncSession) -> None:
    created = (await reseller_client.post(BASE, json=payload())).json()
    did = created["id"]

    resp = await reseller_client.get(f"{BASE}/{did}")
    assert resp.status_code == 200 and resp.json()["mac_address"] == "AA:BB:CC:DD:EE:01"

    resp = await reseller_client.put(
        f"{BASE}/{did}",
        json={
            "client_name": "Novo nome",
            "playlist_name": "Lista 2",
            "playlist_url": "http://novo.host/lista.m3u",
            "license_expires_at": "2027-06-30",
        },
    )
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["client_name"] == "Novo nome"
    assert body["playlist_name"] == "Lista 2"
    assert body["playlist_url"] == "http://novo.host/lista.m3u"
    assert body["playlist_host"] == "http://novo.host"
    assert body["license_expires_at"] == "2027-06-30"
    assert body["playlists_count"] == 1

    resp = await reseller_client.delete(f"{BASE}/{did}")
    assert resp.status_code == 200
    assert (await reseller_client.get(f"{BASE}/{did}")).status_code == 404
    assert (await reseller_client.delete(f"{BASE}/{did}")).status_code == 404
    assert (await db.scalars(select(Playlist))).all() == []  # cascade

    actions = (await db.scalars(select(AuditLog.action).order_by(AuditLog.id))).all()
    assert actions == ["device.create", "device.update", "device.delete"]


async def test_cannot_touch_other_resellers_device(
    reseller_client: AsyncClient, db: AsyncSession
) -> None:
    other = Reseller(username="outra", name="Outra", password_hash=hash_password("x"))
    db.add(other)
    await db.flush()
    device = Device(mac_address="AA:BB:CC:DD:EE:02", reseller_id=other.id)
    db.add(device)
    await db.flush()
    assert (await reseller_client.get(f"{BASE}/{device.id}")).status_code == 404
    assert (await reseller_client.delete(f"{BASE}/{device.id}")).status_code == 404
    resp = await reseller_client.post(f"{BASE}/batch-delete", json={"ids": [device.id]})
    assert resp.status_code == 200 and resp.json()["deleted"] == 0
    assert await db.get(Device, device.id) is not None


async def test_list_search_pagination_and_batch_delete(
    reseller_client: AsyncClient, db: AsyncSession
) -> None:
    ids = []
    for i in range(3):
        resp = await reseller_client.post(
            BASE, json=payload(f"AA:BB:CC:DD:EE:0{i + 1}", client_name=f"Cliente {i + 1}")
        )
        ids.append(resp.json()["id"])

    resp = await reseller_client.get(BASE)
    assert resp.json()["total"] == 3
    assert [d["client_name"] for d in resp.json()["items"]][0] == "Cliente 3"  # newest first

    resp = await reseller_client.get(BASE, params={"search": "ee:02"})
    assert [d["mac_address"] for d in resp.json()["items"]] == ["AA:BB:CC:DD:EE:02"]
    resp = await reseller_client.get(BASE, params={"search": "cliente 1"})
    assert resp.json()["total"] == 1
    resp = await reseller_client.get(BASE, params={"per_page": 2, "page": 2})
    assert resp.json()["total"] == 3 and len(resp.json()["items"]) == 1

    device = await db.get(Device, ids[0])
    assert device is not None
    device.license_expires_at = date.today() - timedelta(days=1)
    await db.flush()
    resp = await reseller_client.get(BASE, params={"status": "expired"})
    assert [d["id"] for d in resp.json()["items"]] == [ids[0]]
    assert resp.json()["items"][0]["status"] == "expired"
    resp = await reseller_client.get(BASE, params={"status": "active"})
    assert resp.json()["total"] == 2

    resp = await reseller_client.post(f"{BASE}/batch-delete", json={"ids": ids[:2]})
    assert resp.status_code == 200 and resp.json()["deleted"] == 2
    assert (await reseller_client.get(BASE)).json()["total"] == 1
    deletes = (await db.scalars(select(AuditLog).where(AuditLog.action == "device.delete"))).all()
    assert len(deletes) == 2
