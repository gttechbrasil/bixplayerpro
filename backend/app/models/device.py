from datetime import date, datetime
from typing import TYPE_CHECKING

from sqlalchemy import CHAR, BigInteger, Date, DateTime, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin

if TYPE_CHECKING:
    from app.models.playlist import Playlist
    from app.models.reseller import Reseller


class Device(TimestampMixin, Base):
    __tablename__ = "devices"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    reseller_id: Mapped[int | None] = mapped_column(
        BigInteger, ForeignKey("resellers.id", ondelete="SET NULL"), nullable=True, index=True
    )
    mac_address: Mapped[str] = mapped_column(CHAR(17), unique=True, nullable=False)
    device_id: Mapped[str | None] = mapped_column(String(64), unique=True, nullable=True)
    client_name: Mapped[str | None] = mapped_column(String(120), nullable=True)
    token_hash: Mapped[str | None] = mapped_column(String(64), unique=True, nullable=True)
    app_type: Mapped[str | None] = mapped_column(String(16), nullable=True)
    app_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    license_expires_at: Mapped[date | None] = mapped_column(Date, nullable=True)
    last_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    reseller: Mapped["Reseller | None"] = relationship(back_populates="devices")
    playlists: Mapped[list["Playlist"]] = relationship(
        back_populates="device",
        cascade="all, delete-orphan",
        order_by="Playlist.position, Playlist.id",
    )

    @property
    def is_registered(self) -> bool:
        return self.reseller_id is not None

    def license_expired(self, today: date) -> bool:
        return self.license_expires_at is not None and self.license_expires_at < today
