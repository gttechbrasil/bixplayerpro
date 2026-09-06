import os

from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AuditLog, Banner, Reseller
from tests.test_device import CONFIG, bearer, register
from tests.test_reseller_devices import BASE as DEVICES
from tests.test_reseller_devices import payload

BRANDING = "/api/v1/reseller/branding"
UPLOAD = f"{BRANDING}/upload"
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 64
JPG = b"\xff\xd8\xff\xe0" + b"\x00" * 64
WEBP = b"RIFF\x00\x00\x00\x00WEBPVP8 " + b"\x00" * 64


async def test_branding_get_and_update(
    reseller_client: AsyncClient, client: AsyncClient, db: AsyncSession
) -> None:
    resp = await reseller_client.get(BRANDING)
    assert resp.status_code == 200
    assert resp.json() == {
        "logo_url": None,
        "bg_url": None,
        "qr_content": None,
        "theme": "default",
        "auto_ads": False,
    }
    resp = await reseller_client.put(
        BRANDING,
        json={
            "logo_url": "https://cdn/logo.png",
            "qr_content": "  https://wa.me/55  ",
            "theme": "grid",
            "auto_ads": True,
        },
    )
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["logo_url"] == "https://cdn/logo.png"
    assert body["qr_content"] == "https://wa.me/55"
    assert body["theme"] == "grid" and body["auto_ads"] is True
    assert body["bg_url"] is None

    assert (await reseller_client.put(BRANDING, json={"theme": "theme_8"})).status_code == 422
    resp = await reseller_client.put(BRANDING, json={"logo_url": ""})
    assert resp.json()["logo_url"] is None

    # the app sees the branding once the device is registered
    reg = await register(client, "dev-brand")
    await reseller_client.post(DEVICES, json=payload(reg["mac_address"]))
    cfg = (await client.get(CONFIG, headers=bearer(reg["token"]))).json()
    assert cfg["theme"] == "grid" and cfg["qr_content"] == "https://wa.me/55" and cfg["auto_ads"]


async def test_upload_images(reseller_client: AsyncClient, db: AsyncSession) -> None:
    resp = await reseller_client.post(
        UPLOAD, params={"kind": "logo"}, files={"file": ("logo.png", PNG, "image/png")}
    )
    assert resp.status_code == 200, resp.text
    url = resp.json()["url"]
    assert url.startswith("http://test/uploads/r") and url.endswith(".png")
    stored = os.path.join(os.environ["UPLOAD_DIR"], url.rsplit("/", 1)[1])
    assert os.path.exists(stored)  # noqa: ASYNC240
    assert (await reseller_client.get(BRANDING)).json()["logo_url"] == url

    resp = await reseller_client.post(
        UPLOAD, params={"kind": "bg"}, files={"file": ("bg.jpg", JPG, "image/jpeg")}
    )
    assert resp.status_code == 200 and resp.json()["url"].endswith(".jpg")
    assert (await reseller_client.get(BRANDING)).json()["bg_url"] == resp.json()["url"]

    resp = await reseller_client.post(
        UPLOAD, params={"kind": "bg"}, files={"file": ("x.webp", WEBP, "image/webp")}
    )
    assert resp.status_code == 200 and resp.json()["url"].endswith(".webp")

    resp = await reseller_client.post(
        UPLOAD, files={"file": ("x.png", b"GIF89a" + b"\x00" * 10, "image/png")}
    )
    assert resp.status_code == 400 and resp.json()["detail"]["code"] == "unsupported_image"

    big = PNG + b"\x00" * (2 * 1024 * 1024)
    resp = await reseller_client.post(UPLOAD, files={"file": ("big.png", big, "image/png")})
    assert resp.status_code == 400 and resp.json()["detail"]["code"] == "upload_too_large"

    resp = await reseller_client.post(
        UPLOAD, params={"kind": "icon"}, files={"file": ("a.png", PNG)}
    )
    assert resp.status_code == 422

    uploads = (await db.scalars(select(AuditLog).where(AuditLog.action == "branding.upload"))).all()
    assert len(uploads) == 3


async def test_uploaded_file_is_served(client: AsyncClient, reseller_client: AsyncClient) -> None:
    resp = await reseller_client.post(UPLOAD, files={"file": ("logo.png", PNG, "image/png")})
    name = resp.json()["url"].rsplit("/", 1)[1]
    served = await client.get(f"/uploads/{name}")
    assert served.status_code == 200 and served.content == PNG


async def test_banners_crud_and_limit(
    reseller_client: AsyncClient, client: AsyncClient, db: AsyncSession
) -> None:
    b = f"{BRANDING}/banners"
    assert (await reseller_client.get(b)).json() == []

    resp = await reseller_client.post(b, json={"title": "Promo", "url": "https://cdn/p.jpg"})
    assert resp.status_code == 201, resp.text
    bid = resp.json()["id"]
    assert resp.json()["is_active"] is True
    bad = await reseller_client.post(b, json={"title": "x", "url": "cdn/p.jpg"})
    assert bad.status_code == 400

    resp = await reseller_client.patch(f"{b}/{bid}", json={"is_active": False, "title": "Promo 2"})
    assert resp.status_code == 200 and resp.json()["is_active"] is False
    assert resp.json()["title"] == "Promo 2"

    # only active banners reach the app
    reg = await register(client, "dev-banner")
    await reseller_client.post(DEVICES, json=payload(reg["mac_address"]))
    cfg = (await client.get(CONFIG, headers=bearer(reg["token"]))).json()
    assert cfg["banners"] == []
    await reseller_client.patch(f"{b}/{bid}", json={"is_active": True})
    cfg = (await client.get(CONFIG, headers=bearer(reg["token"]))).json()
    assert [x["title"] for x in cfg["banners"]] == ["Promo 2"]

    for i in range(9):
        created = await reseller_client.post(b, json={"title": f"b{i}", "url": "https://cdn/x"})
        assert created.status_code == 201
    resp = await reseller_client.post(b, json={"title": "b10", "url": "https://cdn/x"})
    assert resp.status_code == 400 and resp.json()["detail"]["code"] == "banner_limit"
    assert len((await reseller_client.get(b)).json()) == 10

    resp = await reseller_client.delete(f"{b}/{bid}")
    assert resp.status_code == 200
    assert (await reseller_client.delete(f"{b}/{bid}")).status_code == 404
    assert len((await reseller_client.get(b)).json()) == 9

    other = Reseller(username="outra", name="Outra", password_hash="x")
    db.add(other)
    await db.flush()
    foreign = Banner(reseller_id=other.id, title="f", url="https://x")
    db.add(foreign)
    await db.flush()
    assert (
        await reseller_client.patch(f"{b}/{foreign.id}", json={"title": "h"})
    ).status_code == 404
    assert (await reseller_client.delete(f"{b}/{foreign.id}")).status_code == 404

    actions = set((await db.scalars(select(AuditLog.action))).all())
    assert {"banner.create", "banner.update", "banner.delete"} <= actions


async def test_profile_and_password_change(
    reseller_client: AsyncClient, client: AsyncClient
) -> None:
    resp = await reseller_client.get("/api/v1/reseller/profile")
    assert resp.status_code == 200
    assert resp.json()["username"] == "revenda" and resp.json()["name"] == "Revenda Teste"

    resp = await reseller_client.put(
        "/api/v1/reseller/profile/password",
        json={"current_password": "errada", "new_password": "novasenha1"},
    )
    assert resp.status_code == 400 and resp.json()["detail"]["code"] == "wrong_password"
    resp = await reseller_client.put(
        "/api/v1/reseller/profile/password",
        json={"current_password": "revenda123", "new_password": "novasenha1"},
    )
    assert resp.status_code == 200
    login = await client.post(
        "/api/v1/auth/reseller/login", json={"username": "revenda", "password": "novasenha1"}
    )
    assert login.status_code == 200


async def test_branding_urls_must_be_http(reseller_client) -> None:
    resp = await reseller_client.put(
        "/api/v1/reseller/branding", json={"logo_url": "javascript:alert(1)"}
    )
    assert resp.status_code == 422
    resp = await reseller_client.put(
        "/api/v1/reseller/branding", json={"bg_url": "https://cdn.exemplo.com/fundo.jpg"}
    )
    assert resp.status_code == 200
    assert resp.json()["bg_url"] == "https://cdn.exemplo.com/fundo.jpg"
