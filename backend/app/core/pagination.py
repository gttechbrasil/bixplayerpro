"""Offset pagination helper for SQLAlchemy selects."""

from collections.abc import Callable
from typing import Any

from sqlalchemy import Select, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.schemas.common import Page, PageParams


async def paginate[T](
    db: AsyncSession,
    stmt: Select[Any],
    params: PageParams,
    mapper: Callable[[Any], T],
) -> Page[T]:
    total = await db.scalar(select(func.count()).select_from(stmt.order_by(None).subquery()))
    rows = (await db.execute(stmt.offset(params.offset).limit(params.per_page))).all()
    items = [mapper(row[0] if len(row) == 1 else row) for row in rows]
    return Page(items=items, total=total or 0, page=params.page, per_page=params.per_page)
