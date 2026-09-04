from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AuditLog, Reseller
from tests.test_device import CONFIG, PLAYLISTS, XTREAM, attach_to_reseller, bearer, register


async def test_unregistered_device_cannot_manage_playlists(client: AsyncClient) -> None:
    data = await register(client)
    resp = await client.post(
        PLAYLISTS, headers=bearer(data["token"]), json={"name": "x", "url": XTREAM}
    )
    assert resp.status_code == 403
    assert resp.json()["detail"]["code"] == "device_not_registered"
    resp = await client.delete(f"{PLAYLISTS}/1", headers=bearer(data["token"]))
    assert resp.status_code == 403


async def test_add_and_delete_playlist(
    client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    data = await register(client)
    await attach_to_reseller(db, data["mac_address"], reseller_user)
    h = bearer(data["token"])

    r1 = await client.post(PLAYLISTS, headers=h, json={"name": "Xtream", "url": XTREAM})
    r2 = await client.post(
        PLAYLISTS, headers=h, json={"name": "M3U", "url": "http://x.y/l.m3u", "is_protected": True}
    )
    assert r1.status_code == 201 and r2.status_code == 201
    assert r2.json()["type"] == "m3u" and r2.json()["is_protected"] is True

    cfg = (await client.get(CONFIG, headers=h)).json()
    assert [p["name"] for p in cfg["playlists"]] == ["Xtream", "M3U"]

    resp = await client.delete(f"{PLAYLISTS}/{r1.json()['id']}", headers=h)
    assert resp.status_code == 200
    cfg = (await client.get(CONFIG, headers=h)).json()
    assert [p["name"] for p in cfg["playlists"]] == ["M3U"]

    resp = await client.delete(f"{PLAYLISTS}/{r1.json()['id']}", headers=h)
    assert resp.status_code == 404

    actions = (await db.scalars(select(AuditLog.action).order_by(AuditLog.id))).all()
    assert actions == ["playlist.create", "playlist.create", "playlist.delete"]


async def test_invalid_playlist_url(
    client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    data = await register(client)
    await attach_to_reseller(db, data["mac_address"], reseller_user)
    resp = await client.post(
        PLAYLISTS, headers=bearer(data["token"]), json={"name": "x", "url": "not-a-url"}
    )
    assert resp.status_code == 400
    assert resp.json()["detail"]["code"] == "invalid_playlist_url"


async def test_cannot_delete_other_devices_playlist(
    client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    a = await register(client, "dev-a")
    b = await register(client, "dev-b")
    await attach_to_reseller(db, a["mac_address"], reseller_user)
    await attach_to_reseller(db, b["mac_address"], reseller_user)
    created = await client.post(
        PLAYLISTS, headers=bearer(a["token"]), json={"name": "x", "url": XTREAM}
    )
    resp = await client.delete(f"{PLAYLISTS}/{created.json()['id']}", headers=bearer(b["token"]))
    assert resp.status_code == 404
