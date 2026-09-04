"""Audit log writer."""

from typing import Any

from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AuditLog


async def record(
    db: AsyncSession,
    *,
    actor_type: str,
    actor_id: int | None,
    action: str,
    target: str | None = None,
    payload: dict[str, Any] | None = None,
    ip: str | None = None,
) -> AuditLog:
    entry = AuditLog(
        actor_type=actor_type,
        actor_id=actor_id,
        action=action,
        target=target,
        payload=payload,
        ip=ip,
    )
    db.add(entry)
    await db.flush()
    return entry
