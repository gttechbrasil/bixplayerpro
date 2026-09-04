from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import hash_password
from app.models import AuditLog, Device, Playlist, Reseller
from app.services.playlists import normalize_host
from tests.test_device import CONFIG, bearer, register
from tests.test_reseller_devices import BASE as DEVICES
from tests.test_reseller_devices import payload

DNS = "/api/v1/reseller/dns"


def test_normalize_host() -> None:
    assert normalize_host("novo.com") == "http://novo.com"
    assert normalize_host("HTTP://Novo.com:8080/path?x=1") == "http://novo.com:8080"
    assert normalize_host(" https://a.b/ ") == "https://a.b"


async def test_list_hosts_and_migrate(
    reseller_client: AsyncClient, client: AsyncClient, db: AsyncSession
) -> None:
    reg = await register(client, "dev-dns")
    await reseller_client.post(DEVICES, json=payload(reg["mac_address"]))
    await reseller_client.post(
        DEVICES,
        json=payload(
            "AA:BB:CC:DD:EE:02", playlist_url="http://cnplay.click/get.php?username=b&password=c"
        ),
    )
    await reseller_client.post(
        DEVICES, json=payload("AA:BB:CC:DD:EE:03", playlist_url="https://outro.tv/lista.m3u")
    )
    # another reseller's playlist on the same host must be untouched
    other = Reseller(username="outra", name="Outra", password_hash=hash_password("x"))
    db.add(other)
    await db.flush()
    dev = Device(mac_address="AA:BB:CC:DD:EE:99", reseller_id=other.id)
    db.add(dev)
    await db.flush()
    db.add(
        Playlist(
            device_id=dev.id,
            name="x",
            url="http://cnplay.click/l.m3u",
            type="m3u",
            host="http://cnplay.click",
        )
    )
    await db.flush()

    resp = await reseller_client.get(DNS)
    assert resp.status_code == 200
    assert resp.json() == [
        {"host": "http://cnplay.click", "playlists": 2},
        {"host": "https://outro.tv", "playlists": 1},
    ]

    resp = await reseller_client.post(
        f"{DNS}/migrate", json={"from_host": "http://cnplay.click", "to_host": "cnplay.click"}
    )
    assert resp.status_code == 422
    assert resp.json()["detail"]["code"] == "same_host"

    resp = await reseller_client.post(
        f"{DNS}/migrate", json={"from_host": "http://cnplay.click", "to_host": "novodns.net:8080"}
    )
    assert resp.status_code == 200, resp.text
    assert resp.json()["affected"] == 2
    assert resp.json()["to_host"] == "http://novodns.net:8080"

    resp = await reseller_client.get(DNS)
    assert resp.json() == [
        {"host": "http://novodns.net:8080", "playlists": 2},
        {"host": "https://outro.tv", "playlists": 1},
    ]

    cfg = (await client.get(CONFIG, headers=bearer(reg["token"]))).json()
    assert cfg["playlists"][0]["url"].startswith("http://novodns.net:8080/get.php?username=u1")
    assert "password=p1" in cfg["playlists"][0]["url"]

    untouched = await db.scalar(select(Playlist).where(Playlist.device_id == dev.id))
    assert untouched is not None and untouched.host == "http://cnplay.click"

    entry = await db.scalar(select(AuditLog).where(AuditLog.action == "dns.migrate"))
    assert entry is not None and entry.payload["affected"] == 2

    resp = await reseller_client.post(
        f"{DNS}/migrate", json={"from_host": "http://nada.com", "to_host": "http://x.com"}
    )
    assert resp.json()["affected"] == 0
    resp = await reseller_client.post(
        f"{DNS}/migrate", json={"from_host": "abc", "to_host": "ftp://x"}
    )
    assert resp.status_code == 400
