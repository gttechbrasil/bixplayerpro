from datetime import datetime
from typing import Any

from sqlalchemy import BigInteger, DateTime, Index, String, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class AuditLog(Base):
    __tablename__ = "audit_log"
    __table_args__ = (Index("ix_audit_log_actor", "actor_type", "actor_id"),)

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    actor_type: Mapped[str] = mapped_column(String(16), nullable=False)
    actor_id: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    action: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    target: Mapped[str | None] = mapped_column(String(64), nullable=True)
    payload: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)
    ip: Mapped[str | None] = mapped_column(String(45), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False, index=True
    )
