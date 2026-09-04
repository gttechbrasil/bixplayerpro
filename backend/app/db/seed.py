"""Idempotent development/production seed.

Creates the first admin (ADMIN_USERNAME/ADMIN_PASSWORD), default settings and, outside
production, a test reseller (`revenda` / `revenda123`).

    python -m app.db.seed
"""

import asyncio
import logging
from datetime import date, timedelta

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.security import hash_password
from app.db.session import dispose_engine, get_session_factory
from app.models import Admin, Reseller, Setting
from app.services.settings import DEFAULT_SETTINGS

log = logging.getLogger("seed")

TEST_RESELLER = {"username": "revenda", "password": "revenda123", "name": "Revenda de Teste"}


async def seed(session: AsyncSession) -> None:
    settings = get_settings()

    admin = await session.scalar(select(Admin).where(Admin.username == settings.admin_username))
    if admin is None:
        session.add(
            Admin(
                username=settings.admin_username,
                password_hash=hash_password(settings.admin_password),
            )
        )
        log.info("created admin %s", settings.admin_username)

    existing = {s.key for s in (await session.scalars(select(Setting))).all()}
    defaults = dict(DEFAULT_SETTINGS)
    defaults["platform_name"] = settings.platform_name
    for key, value in defaults.items():
        if key not in existing:
            session.add(Setting(key=key, value=value))
            log.info("created setting %s", key)

    if not settings.is_production:
        reseller = await session.scalar(
            select(Reseller).where(Reseller.username == TEST_RESELLER["username"])
        )
        if reseller is None:
            session.add(
                Reseller(
                    username=TEST_RESELLER["username"],
                    name=TEST_RESELLER["name"],
                    password_hash=hash_password(TEST_RESELLER["password"]),
                    credits=10,
                    expires_at=date.today() + timedelta(days=365),
                )
            )
            log.info("created test reseller %s", TEST_RESELLER["username"])

    await session.commit()


async def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    async with get_session_factory()() as session:
        await seed(session)
    await dispose_engine()


if __name__ == "__main__":
    asyncio.run(main())
