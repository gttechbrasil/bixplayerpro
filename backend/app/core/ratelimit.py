"""Redis fixed-window rate limiting for the public device endpoints.

`/device/register` needs no credentials and `/device/config` is hit by every app at boot, so
both get a budget per client IP (generous: many TVs share one CGNAT address) and a tighter one
per device. Limits and window come from the settings (`DEVICE_*` variables). If Redis is
unreachable the check fails open with a warning: a Redis outage must not take the apps down.
"""

import logging

from fastapi import Depends, Request
from redis.exceptions import RedisError

from app.core.config import get_settings
from app.core.deps import current_device, get_client_ip
from app.core.exceptions import ApiError
from app.core.redis import get_redis
from app.core.security import hash_device_identifier
from app.models import Device
from app.schemas.device import DeviceRegisterRequest

log = logging.getLogger(__name__)

MSG_RATE_LIMITED = "Muitas requisições. Aguarde um instante e tente novamente."


async def consume(key: str, limit: int, window: int) -> None:
    """Counts one hit on `key`; raises 429 once the window budget is spent."""
    if limit <= 0:
        return
    try:
        redis = get_redis()
        async with redis.pipeline(transaction=True) as pipe:
            pipe.incr(key)
            pipe.ttl(key)
            count, ttl = await pipe.execute()
        if ttl is None or ttl < 0:
            # First hit of the window (or a key left without TTL): start the clock now.
            await redis.expire(key, window)
            ttl = window
    except (RedisError, OSError) as exc:  # pragma: no cover - depends on the environment
        log.warning("rate limit skipped (redis unavailable): %s", exc)
        return
    if int(count) > limit:
        raise ApiError(
            429,
            MSG_RATE_LIMITED,
            "rate_limited",
            headers={"Retry-After": str(max(1, int(ttl)))},
            retry_after=max(1, int(ttl)),
        )


async def limit_device_register(request: Request, body: DeviceRegisterRequest) -> None:
    settings = get_settings()
    ip = get_client_ip(request) or "-"
    await consume(
        f"rl:register:ip:{ip}", settings.device_register_rate_limit, settings.device_rate_window
    )
    await consume(
        f"rl:register:dev:{hash_device_identifier(body.device_id)}",
        settings.device_rate_limit,
        settings.device_rate_window,
    )


async def device_with_rate_limit(
    request: Request, device: Device = Depends(current_device)
) -> Device:
    """`CurrentDevice` plus the per-IP and per-device budgets for `/device/config`."""
    settings = get_settings()
    ip = get_client_ip(request) or "-"
    await consume(f"rl:config:ip:{ip}", settings.device_rate_limit_ip, settings.device_rate_window)
    await consume(
        f"rl:config:dev:{device.id}", settings.device_rate_limit, settings.device_rate_window
    )
    return device
