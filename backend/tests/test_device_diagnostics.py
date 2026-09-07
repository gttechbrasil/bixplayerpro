"""POST /device/diagnostics (app) and the admin read side (M5-013)."""

from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Reseller
from tests.test_device import CONFIG, attach_to_reseller, bearer, register

DIAG = "/api/v1/device/diagnostics"


async def _registered_device(client: AsyncClient, db: AsyncSession, reseller: Reseller):
    data = await register(client, "diag-device")
    device = await attach_to_reseller(db, data["mac_address"], reseller)
    assert (await client.get(CONFIG, headers=bearer(data["token"]))).status_code == 200
    return device, data["token"]


async def test_device_sends_and_admin_reads(
    client: AsyncClient, admin_client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    device, token = await _registered_device(client, db, reseller_user)
    resp = await client.post(
        DIAG,
        headers=bearer(token),
        json={
            "kind": "crash",
            "app_version": "1.2.1",
            "device_info": {"model": "X96 Mini", "android": "9", "abi": "armeabi-v7a"},
            "log": "FATAL EXCEPTION main\njava.lang.IllegalStateException: boom\n",
        },
    )
    assert resp.status_code == 201, resp.text
    diag_id = resp.json()["id"]

    listing = await admin_client.get(
        f"/api/v1/admin/resellers/{reseller_user.id}/devices/{device.id}/diagnostics"
    )
    assert listing.status_code == 200
    items = listing.json()
    assert [i["id"] for i in items] == [diag_id]
    assert items[0]["kind"] == "crash"
    assert items[0]["device_info"]["model"] == "X96 Mini"
    assert "body" not in items[0]

    detail = await admin_client.get(
        f"/api/v1/admin/resellers/{reseller_user.id}/devices/{device.id}/diagnostics/{diag_id}"
    )
    assert detail.status_code == 200
    assert "IllegalStateException" in detail.json()["body"]
    assert detail.json()["size"] > 0


async def test_unregistered_device_cannot_send_without_token(client: AsyncClient) -> None:
    resp = await client.post(DIAG, json={"log": "x"})
    assert resp.status_code == 401


async def test_body_limit_and_retention(
    client: AsyncClient, admin_client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    device, token = await _registered_device(client, db, reseller_user)
    too_big = await client.post(DIAG, headers=bearer(token), json={"log": "x" * (512 * 1024 + 1)})
    assert too_big.status_code == 422

    for n in range(12):
        resp = await client.post(DIAG, headers=bearer(token), json={"log": f"report {n}"})
        assert resp.status_code in (201, 429), resp.text
        if resp.status_code == 429:
            break
    listing = await admin_client.get(
        f"/api/v1/admin/resellers/{reseller_user.id}/devices/{device.id}/diagnostics"
    )
    assert len(listing.json()) <= 10


async def test_admin_cannot_read_devices_of_another_reseller(
    client: AsyncClient, admin_client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    device, token = await _registered_device(client, db, reseller_user)
    assert (await client.post(DIAG, headers=bearer(token), json={"log": "hi"})).status_code == 201
    other = await admin_client.get(
        f"/api/v1/admin/resellers/{reseller_user.id + 999}/devices/{device.id}/diagnostics"
    )
    assert other.status_code == 404
