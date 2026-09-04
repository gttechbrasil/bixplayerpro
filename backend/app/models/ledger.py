from datetime import datetime

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class CreditLedger(Base):
    __tablename__ = "credit_ledger"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    reseller_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("resellers.id", ondelete="SET NULL"), nullable=True, index=True
    )
    delta: Mapped[int] = mapped_column(Integer, nullable=False)
    balance_after: Mapped[int] = mapped_column(Integer, nullable=False)
    reason: Mapped[str] = mapped_column(String(32), nullable=False)
    note: Mapped[str | None] = mapped_column(Text, nullable=True)
    ref: Mapped[str | None] = mapped_column(String(64), nullable=True)
    actor_type: Mapped[str] = mapped_column(String(16), nullable=False)
    actor_id: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False, index=True
    )
