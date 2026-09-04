import re
from datetime import date, timedelta

from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Banner, Device, Playlist, Reseller

REGISTER = "/api/v1/device/register"
CONFIG = "/api/v1/device/config"
PLAYLISTS = "/api/v1/device/playlists"
XTREAM = "http://cnplay.click/get.php?username=u1&password=p1&type=m3u_plus&output=hls"


async def register(client: AsyncClient, device_id: str = "android-id-1") -> dict:
    resp = await client.post(
        REGISTER, json={"device_id": device_id, "app_type": "tv", "app_version": "1.0.0"}
    )
    assert resp.status_code == 200, resp.text
    return resp.json()


def bearer(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


async def attach_to_reseller(db: AsyncSession, mac: str, reseller: Reseller) -> Device:
    device = await db.scalar(select(Device).where(Device.mac_address == mac))
    assert device is not None
    device.reseller_id = reseller.id
    device.client_name = "João"
    await db.flush()
    return device


async def test_new_device_is_unregistered(client: AsyncClient) -> None:
    data = await register(client)
    assert re.fullmatch(r"02:50:50:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}", data["mac_address"])
    assert len(data["token"]) == 64

    resp = await client.get(CONFIG, headers=bearer(data["token"]))
    assert resp.status_code == 200
    cfg = resp.json()
    assert cfg["registered"] is False
    assert cfg["status"] == "unregistered"
    assert cfg["mac_address"] == data["mac_address"]
    assert cfg["playlists"] == []
    assert cfg["theme"] == "theme_d"
    assert cfg["pin"] == "0000"
    assert cfg["min_app_version"] == "1.0.0"


async def test_reregister_keeps_mac_and_rotates_token(client: AsyncClient) -> None:
    first = await register(client)
    second = await register(client)
    assert first["mac_address"] == second["mac_address"]
    assert first["token"] != second["token"]
    assert (await client.get(CONFIG, headers=bearer(first["token"]))).status_code == 401
    assert (await client.get(CONFIG, headers=bearer(second["token"]))).status_code == 200


async def test_two_devices_get_different_macs(client: AsyncClient) -> None:
    a = await register(client, "dev-a")
    b = await register(client, "dev-b")
    assert a["mac_address"] != b["mac_address"]


async def test_registered_device_gets_playlists_and_branding(
    client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    data = await register(client)
    device = await attach_to_reseller(db, data["mac_address"], reseller_user)
    reseller_user.theme = "theme_8"
    reseller_user.logo_url = "https://cdn/logo.png"
    db.add(Banner(reseller_id=reseller_user.id, title="Promo", url="https://cdn/b.jpg"))
    db.add(
        Banner(reseller_id=reseller_user.id, title="Off", url="https://cdn/x.jpg", is_active=False)
    )
    await db.flush()

    resp = await client.post(
        PLAYLISTS, headers=bearer(data["token"]), json={"name": "Lista 1", "url": XTREAM}
    )
    assert resp.status_code == 201, resp.text
    assert "password=p1" in resp.json()["url"]

    resp = await client.get(CONFIG, headers=bearer(data["token"]))
    cfg = resp.json()
    assert cfg["registered"] is True
    assert cfg["status"] == "active"
    assert cfg["client_name"] == "João"
    assert cfg["theme"] == "theme_8"
    assert cfg["logo_url"] == "https://cdn/logo.png"
    assert [b["title"] for b in cfg["banners"]] == ["Promo"]
    assert len(cfg["playlists"]) == 1
    assert cfg["playlists"][0]["type"] == "xtream"
    assert "password=p1" in cfg["playlists"][0]["url"]

    await db.refresh(device)
    assert device.last_seen_at is not None
    playlist = await db.scalar(select(Playlist).where(Playlist.device_id == device.id))
    assert playlist is not None and "p1" not in playlist.url and playlist.password_enc


async def test_expired_license_hides_playlists(
    client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    data = await register(client)
    device = await attach_to_reseller(db, data["mac_address"], reseller_user)
    db.add(
        Playlist(device_id=device.id, name="L", url="http://a/b.m3u", type="m3u", host="http://a")
    )
    device.license_expires_at = date.today() - timedelta(days=1)
    await db.flush()
    cfg = (await client.get(CONFIG, headers=bearer(data["token"]))).json()
    assert cfg["status"] == "expired"
    assert cfg["registered"] is True
    assert cfg["playlists"] == []


async def test_blocked_or_expired_reseller_expires_device(
    client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    data = await register(client)
    await attach_to_reseller(db, data["mac_address"], reseller_user)
    h = bearer(data["token"])
    reseller_user.is_blocked = True
    await db.flush()
    assert (await client.get(CONFIG, headers=h)).json()["status"] == "expired"

    reseller_user.is_blocked = False
    reseller_user.expires_at = date.today() - timedelta(days=1)
    await db.flush()
    assert (await client.get(CONFIG, headers=h)).json()["status"] == "expired"

    reseller_user.expires_at = None
    await db.flush()
    assert (await client.get(CONFIG, headers=h)).json()["status"] == "active"
