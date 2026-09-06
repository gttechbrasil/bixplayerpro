"""Rate limits on the public device endpoints (Redis is flushed before every test)."""

import pytest
from httpx import AsyncClient

from app.core.config import get_settings

REGISTER = "/api/v1/device/register"
CONFIG = "/api/v1/device/config"


async def _register(client: AsyncClient, device_id: str) -> dict:
    resp = await client.post(REGISTER, json={"device_id": device_id, "app_type": "tv"})
    return resp


async def test_register_is_limited_per_ip(client: AsyncClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(get_settings(), "device_register_rate_limit", 3)
    for i in range(3):
        assert (await _register(client, f"android-{i}")).status_code == 200
    blocked = await _register(client, "android-99")
    assert blocked.status_code == 429
    assert blocked.json()["detail"]["code"] == "rate_limited"
    assert int(blocked.headers["retry-after"]) >= 1


async def test_register_is_limited_per_device(client: AsyncClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(get_settings(), "device_rate_limit", 2)
    assert (await _register(client, "same-device")).status_code == 200
    assert (await _register(client, "same-device")).status_code == 200
    assert (await _register(client, "same-device")).status_code == 429
    # Another device from the same IP still gets through: the per-device key is separate.
    assert (await _register(client, "other-device")).status_code == 200


async def test_config_is_limited_per_device(client: AsyncClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(get_settings(), "device_rate_limit", 2)
    token = (await _register(client, "cfg-device")).json()["token"]
    headers = {"Authorization": f"Bearer {token}"}
    assert (await client.get(CONFIG, headers=headers)).status_code == 200
    assert (await client.get(CONFIG, headers=headers)).status_code == 200
    blocked = await client.get(CONFIG, headers=headers)
    assert blocked.status_code == 429
    assert blocked.json()["detail"]["code"] == "rate_limited"


async def test_config_invalid_token_is_not_counted_as_device(client: AsyncClient):
    # Authentication runs first: a bad token is 401, never 429 for a device budget.
    resp = await client.get(CONFIG, headers={"Authorization": "Bearer nope"})
    assert resp.status_code == 401


async def test_limit_zero_disables_the_check(client: AsyncClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(get_settings(), "device_register_rate_limit", 0)
    monkeypatch.setattr(get_settings(), "device_rate_limit", 0)
    for i in range(5):
        assert (await _register(client, f"free-{i}")).status_code == 200
