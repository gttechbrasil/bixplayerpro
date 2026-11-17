"""Google Play review activation (ADR-007)."""

from datetime import date, timedelta

from httpx import AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AuditLog, Device, Reseller, Setting
from tests.test_device import CONFIG, attach_to_reseller, bearer, register

REVIEW = "/api/v1/device/review-code"
DEMO_LIST = "https://bixplayer.pro/demo/lista-revisao.m3u"


async def _configure(db: AsyncSession, reseller: Reseller, code: str = "PLAY-2026") -> None:
    db.add_all(
        [
            Setting(key="play_review_code", value=code),
            Setting(key="play_review_reseller", value=reseller.username),
            Setting(key="play_review_playlist_url", value=DEMO_LIST),
        ]
    )
    await db.flush()


async def test_refused_while_not_configured(client: AsyncClient) -> None:
    data = await register(client)
    resp = await client.post(REVIEW, headers=bearer(data["token"]), json={"code": "x"})
    assert resp.status_code == 403
    assert resp.json()["detail"]["code"] == "review_code_invalid"


async def test_needs_a_device_token(client: AsyncClient) -> None:
    resp = await client.post(REVIEW, json={"code": "PLAY-2026"})
    assert resp.status_code == 401


async def test_wrong_code_is_refused(
    client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    await _configure(db, reseller_user)
    data = await register(client)
    resp = await client.post(REVIEW, headers=bearer(data["token"]), json={"code": "PLAY-2025"})
    assert resp.status_code == 403
    cfg = (await client.get(CONFIG, headers=bearer(data["token"]))).json()
    assert cfg["registered"] is False


async def test_right_code_activates_with_the_demo_list_and_no_credit(
    client: AsyncClient, db: AsyncSession, reseller_user: Reseller, credits_on: None
) -> None:
    await _configure(db, reseller_user)
    credits_before = reseller_user.credits
    data = await register(client)
    h = bearer(data["token"])

    resp = await client.post(REVIEW, headers=h, json={"code": " PLAY-2026 "})
    assert resp.status_code == 200, resp.text

    cfg = (await client.get(CONFIG, headers=h)).json()
    assert cfg["registered"] is True
    assert cfg["status"] == "active"
    assert cfg["license_expires_at"] == str(date.today() + timedelta(days=14))
    assert [(p["name"], p["type"]) for p in cfg["playlists"]] == [("Demonstração", "m3u")]

    await db.refresh(reseller_user)
    assert reseller_user.credits == credits_before
    actions = (await db.scalars(select(AuditLog.action))).all()
    assert "device.review_activate" in actions

    # Again with the same code: still one demo list, license renewed.
    resp = await client.post(REVIEW, headers=h, json={"code": "PLAY-2026"})
    assert resp.status_code == 200
    cfg = (await client.get(CONFIG, headers=h)).json()
    assert len(cfg["playlists"]) == 1


async def test_device_of_another_reseller_is_left_alone(
    client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    other = Reseller(
        username="demo",
        name="Demonstração",
        password_hash="x",
        credits=0,
        expires_at=date.today() + timedelta(days=30),
    )
    db.add(other)
    await db.flush()
    await _configure(db, other)
    data = await register(client)
    await attach_to_reseller(db, data["mac_address"], reseller_user)

    resp = await client.post(REVIEW, headers=bearer(data["token"]), json={"code": "PLAY-2026"})
    assert resp.status_code == 409
    device = await db.scalar(select(Device).where(Device.mac_address == data["mac_address"]))
    assert device is not None and device.reseller_id == reseller_user.id


async def test_blocked_demo_reseller_turns_it_off(
    client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    await _configure(db, reseller_user)
    reseller_user.is_blocked = True
    await db.flush()
    data = await register(client)
    resp = await client.post(REVIEW, headers=bearer(data["token"]), json={"code": "PLAY-2026"})
    assert resp.status_code == 403


async def test_guessing_is_rate_limited(
    client: AsyncClient, db: AsyncSession, reseller_user: Reseller
) -> None:
    await _configure(db, reseller_user)
    data = await register(client)
    h = bearer(data["token"])
    codes = [
        (await client.post(REVIEW, headers=h, json={"code": f"x{i}"})).status_code for i in range(6)
    ]
    assert codes[:5] == [403] * 5
    assert codes[5] == 429


async def test_admin_edits_the_review_settings_without_logging_the_code(
    admin_client: AsyncClient, db: AsyncSession
) -> None:
    resp = await admin_client.put(
        "/api/v1/admin/settings",
        json={
            "play_review_code": "PLAY-2026",
            "play_review_reseller": "demo",
            "play_review_playlist_url": DEMO_LIST,
        },
    )
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["play_review_code"] == "PLAY-2026"
    assert body["play_review_reseller"] == "demo"
    assert body["play_review_playlist_url"] == DEMO_LIST

    resp = await admin_client.put("/api/v1/admin/settings", json={"play_review_code": "não vale"})
    assert resp.status_code == 422

    payloads = (
        await db.scalars(select(AuditLog.payload).where(AuditLog.action == "settings.update"))
    ).all()
    assert payloads and all(p.get("play_review_code") == "***" for p in payloads)
