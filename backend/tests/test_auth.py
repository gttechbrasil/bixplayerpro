from datetime import date, timedelta

from app.core.deps import ADMIN_COOKIE, RESELLER_COOKIE
from app.models import Admin, Reseller
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

from tests.conftest import ADMIN_PASSWORD, RESELLER_PASSWORD

LOGIN_ADMIN = "/api/v1/auth/admin/login"
LOGIN_RESELLER = "/api/v1/auth/reseller/login"


async def test_admin_login_sets_cookies(client: AsyncClient, admin_user: Admin) -> None:
    resp = await client.post(LOGIN_ADMIN, json={"username": "admin", "password": ADMIN_PASSWORD})
    assert resp.status_code == 200
    body = resp.json()
    assert body["role"] == "admin"
    assert body["user"]["username"] == "admin"
    assert body["csrf_token"]
    assert ADMIN_COOKIE in resp.cookies
    assert resp.cookies["csrf_token"] == body["csrf_token"]
    set_cookie = resp.headers.get_list("set-cookie")
    assert any("HttpOnly" in c and ADMIN_COOKIE in c for c in set_cookie)


async def test_admin_login_wrong_password(client: AsyncClient, admin_user: Admin) -> None:
    resp = await client.post(LOGIN_ADMIN, json={"username": "admin", "password": "nope"})
    assert resp.status_code == 401
    assert resp.json()["detail"]["message"] == "Usuário ou senha inválidos."


async def test_admin_login_rate_limited(client: AsyncClient, admin_user: Admin) -> None:
    for _ in range(5):  # LOGIN_RATE_LIMIT=5 in tests
        resp = await client.post(LOGIN_ADMIN, json={"username": "admin", "password": "nope"})
        assert resp.status_code == 401
    resp = await client.post(LOGIN_ADMIN, json={"username": "admin", "password": ADMIN_PASSWORD})
    assert resp.status_code == 429
    assert "tentativas" in resp.json()["detail"]["message"]


async def test_me_and_logout(admin_client: AsyncClient) -> None:
    resp = await admin_client.get("/api/v1/auth/me")
    assert resp.status_code == 200
    assert resp.json()["role"] == "admin"

    resp = await admin_client.post("/api/v1/auth/logout")
    assert resp.status_code == 200
    resp = await admin_client.get("/api/v1/auth/me")
    assert resp.status_code == 401


async def test_me_without_session(client: AsyncClient) -> None:
    resp = await client.get("/api/v1/auth/me")
    assert resp.status_code == 401


async def test_me_with_garbage_cookie(client: AsyncClient) -> None:
    client.cookies.set(ADMIN_COOKIE, "not-a-jwt")
    resp = await client.get("/api/v1/auth/me")
    assert resp.status_code == 401


async def test_reseller_login_ok(client: AsyncClient, reseller_user: Reseller) -> None:
    resp = await client.post(
        LOGIN_RESELLER, json={"username": "revenda", "password": RESELLER_PASSWORD}
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["role"] == "reseller"
    assert body["user"]["credits"] == 5
    assert RESELLER_COOKIE in resp.cookies
    me = await client.get("/api/v1/auth/me")
    assert me.status_code == 200
    assert me.json()["role"] == "reseller"


async def test_reseller_login_blocked(
    client: AsyncClient, reseller_user: Reseller, db: AsyncSession
) -> None:
    reseller_user.is_blocked = True
    await db.flush()
    resp = await client.post(
        LOGIN_RESELLER, json={"username": "revenda", "password": RESELLER_PASSWORD}
    )
    assert resp.status_code == 403
    assert resp.json()["detail"]["code"] == "reseller_blocked"
    assert "bloqueada" in resp.json()["detail"]["message"]


async def test_reseller_login_expired(
    client: AsyncClient, reseller_user: Reseller, db: AsyncSession
) -> None:
    reseller_user.expires_at = date.today() - timedelta(days=1)
    await db.flush()
    resp = await client.post(
        LOGIN_RESELLER, json={"username": "revenda", "password": RESELLER_PASSWORD}
    )
    assert resp.status_code == 403
    assert resp.json()["detail"]["code"] == "reseller_expired"
    assert "vencida" in resp.json()["detail"]["message"]


async def test_reseller_session_dies_when_blocked_later(
    reseller_client: AsyncClient, reseller_user: Reseller, db: AsyncSession
) -> None:
    assert (await reseller_client.get("/api/v1/auth/me")).status_code == 200
    reseller_user.is_blocked = True
    await db.flush()
    resp = await reseller_client.get("/api/v1/auth/me")
    assert resp.status_code == 403


async def test_reseller_login_wrong_password(client: AsyncClient, reseller_user: Reseller) -> None:
    resp = await client.post(LOGIN_RESELLER, json={"username": "revenda", "password": "x"})
    assert resp.status_code == 401


async def test_device_dependency_rejects_missing_or_bad_token(client: AsyncClient) -> None:
    resp = await client.get("/api/v1/device/config")
    assert resp.status_code in (401, 404)
    resp = await client.get("/api/v1/device/config", headers={"Authorization": "Bearer nope"})
    assert resp.status_code in (401, 404)
