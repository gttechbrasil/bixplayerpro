from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.redis import get_redis
from app.db.session import get_db

router = APIRouter(tags=["health"])


class Health(BaseModel):
    status: str
    database: str
    redis: str


@router.get("/health", summary="Verifica API, banco e Redis")
async def health(db: AsyncSession = Depends(get_db)) -> Health:
    checks = {"status": "ok", "database": "ok", "redis": "ok"}
    try:
        await db.execute(text("SELECT 1"))
    except Exception:  # pragma: no cover
        checks["database"] = "error"
        checks["status"] = "degraded"
    try:
        await get_redis().ping()
    except Exception:  # pragma: no cover
        checks["redis"] = "error"
        checks["status"] = "degraded"
    return Health(**checks)
