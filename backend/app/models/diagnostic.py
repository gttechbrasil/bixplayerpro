from datetime import datetime
from typing import Any

from sqlalchemy import JSON, BigInteger, DateTime, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class DeviceDiagnostic(Base):
    """A log bundle sent by the app (crash captured on the device or a manual report).

    Bodies are capped at 512 KB by the schema and only the newest N per device are kept, so
    the table cannot grow without bound. Read by the admin on the device detail."""

    __tablename__ = "device_diagnostics"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    device_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("devices.id", ondelete="CASCADE"), nullable=False, index=True
    )
    kind: Mapped[str] = mapped_column(String(16), nullable=False, default="manual")
    app_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    device_info: Mapped[dict[str, Any] | None] = mapped_column(JSON, nullable=True)
    size: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
