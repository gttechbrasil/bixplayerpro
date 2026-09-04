from app.core.security import verify_password
from app.db.seed import TEST_RESELLER, seed
from app.models import Admin, Reseller, Setting
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession


async def test_seed_is_idempotent(db: AsyncSession) -> None:
    await seed(db)
    await seed(db)

    admins = (await db.scalars(select(Admin))).all()
    assert len(admins) == 1
    assert admins[0].username == "admin"
    assert verify_password("admin123", admins[0].password_hash)

    resellers = (await db.scalars(select(Reseller))).all()
    assert [r.username for r in resellers] == [TEST_RESELLER["username"]]
    assert resellers[0].credits == 10

    settings = {s.key: s.value for s in (await db.scalars(select(Setting))).all()}
    assert settings["monthly_price"] == "35.00"
    assert settings["packages"] == []
