"""Login logic: credential verification and Redis-backed rate limiting."""

from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.exceptions import too_many_requests, unauthorized
from app.core.redis import get_redis
from app.core.security import verify_password
from app.models import Admin, Reseller

MSG_INVALID_CREDENTIALS = "Usuário ou senha inválidos."


@dataclass
class LoginRateLimiter:
    """Counts failed attempts per (scope, ip, username) in Redis."""

    scope: str
    ip: str | None
    username: str

    @property
    def key(self) -> str:
        return f"login:{self.scope}:{self.ip or '-'}:{self.username.lower()}"

    async def check(self) -> None:
        count = await get_redis().get(self.key)
        if count is not None and int(count) >= get_settings().login_rate_limit:
            raise too_many_requests()

    async def register_failure(self) -> None:
        settings = get_settings()
        redis = get_redis()
        async with redis.pipeline(transaction=True) as pipe:
            pipe.incr(self.key)
            pipe.expire(self.key, settings.login_rate_window)
            await pipe.execute()

    async def reset(self) -> None:
        await get_redis().delete(self.key)


async def authenticate_admin(
    db: AsyncSession, username: str, password: str, ip: str | None
) -> Admin:
    limiter = LoginRateLimiter("admin", ip, username)
    await limiter.check()
    admin = await db.scalar(select(Admin).where(Admin.username == username))
    if admin is None or not verify_password(password, admin.password_hash):
        await limiter.register_failure()
        raise unauthorized(MSG_INVALID_CREDENTIALS, "invalid_credentials")
    await limiter.reset()
    return admin


async def authenticate_reseller(
    db: AsyncSession, username: str, password: str, ip: str | None
) -> Reseller:
    """Returns the reseller when credentials match. Blocked/expired checks are done by
    the caller so that the user gets a specific message only after proving identity."""
    limiter = LoginRateLimiter("reseller", ip, username)
    await limiter.check()
    reseller = await db.scalar(select(Reseller).where(Reseller.username == username))
    if reseller is None or not verify_password(password, reseller.password_hash):
        await limiter.register_failure()
        raise unauthorized(MSG_INVALID_CREDENTIALS, "invalid_credentials")
    await limiter.reset()
    return reseller
