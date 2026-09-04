"""Test fixtures.

Requires a local PostgreSQL (database `iptv_test`) and Redis. Override with
TEST_DATABASE_URL / TEST_REDIS_URL. The schema is created by running the Alembic
migrations once per session; each test runs inside a transaction that is rolled back.
"""

import os
from collections.abc import AsyncIterator

os.environ["APP_ENV"] = "test"
os.environ["DATABASE_URL"] = os.environ.get(
    "TEST_DATABASE_URL", "postgresql+asyncpg://iptv:iptv@localhost:5432/iptv_test"
)
os.environ["REDIS_URL"] = os.environ.get("TEST_REDIS_URL", "redis://localhost:6379/1")
os.environ["SECRET_KEY"] = "test-secret-key-0123456789-test-secret-key"
os.environ["FERNET_KEY"] = "b1oJ7MZ0HSNd1jU7GdvUpcs7f2FZlNPlCzEDGB1tsQE="
os.environ["COOKIE_SECURE"] = "false"
os.environ["ADMIN_USERNAME"] = "admin"
os.environ["ADMIN_PASSWORD"] = "admin123"
os.environ["LOGIN_RATE_LIMIT"] = "5"
os.environ["LOGIN_RATE_WINDOW"] = "60"

import pytest  # noqa: E402
from alembic.config import Config  # noqa: E402
from httpx import ASGITransport, AsyncClient  # noqa: E402
from sqlalchemy import text  # noqa: E402
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker  # noqa: E402

from alembic import command  # noqa: E402
from app.core.redis import get_redis  # noqa: E402
from app.db.session import dispose_engine, get_db, get_engine  # noqa: E402
from app.main import app  # noqa: E402

BACKEND_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


@pytest.fixture(scope="session", autouse=True)
async def _database_schema() -> AsyncIterator[None]:
    """Recreate the public schema and apply all migrations once per test session."""
    engine = get_engine()
    async with engine.begin() as conn:
        await conn.execute(text("DROP SCHEMA public CASCADE"))
        await conn.execute(text("CREATE SCHEMA public"))
    await engine.dispose()

    cfg = Config(os.path.join(BACKEND_DIR, "alembic.ini"))
    cfg.set_main_option("script_location", os.path.join(BACKEND_DIR, "alembic"))
    # alembic's env.py runs its own asyncio.run(); run it in a worker thread so it
    # does not collide with the running test loop.
    import asyncio

    await asyncio.to_thread(command.upgrade, cfg, "head")
    yield
    await dispose_engine()


@pytest.fixture
async def db() -> AsyncIterator[AsyncSession]:
    """A session bound to a connection whose outer transaction is rolled back."""
    engine = get_engine()
    async with engine.connect() as conn:
        trans = await conn.begin()
        factory = async_sessionmaker(
            bind=conn, expire_on_commit=False, join_transaction_mode="create_savepoint"
        )
        async with factory() as session:
            yield session
        await trans.rollback()


@pytest.fixture(autouse=True)
async def _flush_redis() -> AsyncIterator[None]:
    await get_redis().flushdb()
    yield


@pytest.fixture
async def client(db: AsyncSession) -> AsyncIterator[AsyncClient]:
    async def _override_db() -> AsyncIterator[AsyncSession]:
        yield db

    app.dependency_overrides[get_db] = _override_db
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


# ---- actors ------------------------------------------------------------------
from datetime import date, timedelta  # noqa: E402

from app.core.security import hash_password  # noqa: E402
from app.models import Admin, Reseller  # noqa: E402

ADMIN_PASSWORD = "admin123"
RESELLER_PASSWORD = "revenda123"


@pytest.fixture
async def admin_user(db: AsyncSession) -> Admin:
    admin = Admin(username="admin", password_hash=hash_password(ADMIN_PASSWORD))
    db.add(admin)
    await db.flush()
    return admin


@pytest.fixture
async def reseller_user(db: AsyncSession) -> Reseller:
    reseller = Reseller(
        username="revenda",
        name="Revenda Teste",
        password_hash=hash_password(RESELLER_PASSWORD),
        credits=5,
        expires_at=date.today() + timedelta(days=30),
    )
    db.add(reseller)
    await db.flush()
    return reseller


async def login(client: AsyncClient, path: str, username: str, password: str) -> None:
    resp = await client.post(path, json={"username": username, "password": password})
    assert resp.status_code == 200, resp.text
    client.headers["X-CSRF-Token"] = resp.json()["csrf_token"]


@pytest.fixture
async def admin_client(client: AsyncClient, admin_user: Admin) -> AsyncClient:
    await login(client, "/api/v1/auth/admin/login", admin_user.username, ADMIN_PASSWORD)
    return client


@pytest.fixture
async def reseller_client(client: AsyncClient, reseller_user: Reseller) -> AsyncClient:
    await login(client, "/api/v1/auth/reseller/login", reseller_user.username, RESELLER_PASSWORD)
    return client
