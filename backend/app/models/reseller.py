from datetime import date
from typing import TYPE_CHECKING

from sqlalchemy import BigInteger, Boolean, CheckConstraint, Date, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin

if TYPE_CHECKING:
    from app.models.banner import Banner
    from app.models.device import Device

# The two home layouts included in v1 (Anexo I §2.3).
THEMES = ("default", "grid")


class Reseller(TimestampMixin, Base):
    __tablename__ = "resellers"
    __table_args__ = (CheckConstraint("credits >= 0", name="ck_resellers_credits_non_negative"),)

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    username: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    credits: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    expires_at: Mapped[date | None] = mapped_column(Date, nullable=True, index=True)
    is_blocked: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default="false"
    )
    logo_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    bg_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    qr_content: Mapped[str | None] = mapped_column(Text, nullable=True)
    theme: Mapped[str] = mapped_column(
        String(16), nullable=False, default="default", server_default="default"
    )
    auto_ads: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default="false"
    )

    devices: Mapped[list["Device"]] = relationship(back_populates="reseller")
    banners: Mapped[list["Banner"]] = relationship(
        back_populates="reseller", cascade="all, delete-orphan"
    )

    def has_expired(self, today: date) -> bool:
        return self.expires_at is not None and self.expires_at < today

    def is_active(self, today: date) -> bool:
        return not self.is_blocked and not self.has_expired(today)
