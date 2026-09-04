from datetime import date

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select

from app.core.deps import CurrentAdmin, DbSession
from app.core.pagination import paginate
from app.models import AuditLog
from app.schemas.admin import AuditOut
from app.schemas.common import Page, PageParams

router = APIRouter(prefix="/audit-log", tags=["admin: audit"])


@router.get("", response_model=Page[AuditOut])
async def list_audit(
    _: CurrentAdmin,
    db: DbSession,
    params: PageParams = Depends(),
    action: str | None = Query(None, max_length=64),
    actor_type: str | None = Query(None, max_length=16),
    actor_id: int | None = None,
    target: str | None = Query(None, max_length=64),
    date_from: date | None = Query(None, alias="from"),
    date_to: date | None = Query(None, alias="to"),
) -> Page[AuditOut]:
    stmt = select(AuditLog).order_by(AuditLog.id.desc())
    if action:
        stmt = stmt.where(AuditLog.action.ilike(f"{action}%"))
    if actor_type:
        stmt = stmt.where(AuditLog.actor_type == actor_type)
    if actor_id is not None:
        stmt = stmt.where(AuditLog.actor_id == actor_id)
    if target:
        stmt = stmt.where(AuditLog.target == target)
    if date_from:
        stmt = stmt.where(AuditLog.created_at >= date_from)
    if date_to:
        stmt = stmt.where(AuditLog.created_at < date_to.fromordinal(date_to.toordinal() + 1))
    if params.search:
        term = f"%{params.search.strip()}%"
        stmt = stmt.where(AuditLog.action.ilike(term) | AuditLog.target.ilike(term))
    return await paginate(db, stmt, params, AuditOut.model_validate)
