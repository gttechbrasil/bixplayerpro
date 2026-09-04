from typing import TYPE_CHECKING

from sqlalchemy import BigInteger, Boolean, ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin

if TYPE_CHECKING:
    from app.models.reseller import Reseller


class Banner(TimestampMixin, Base):
    __tablename__ = "banners"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    reseller_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("resellers.id", ondelete="CASCADE"), nullable=False, index=True
    )
    title: Mapped[str] = mapped_column(String(120), nullable=False)
    url: Mapped[str] = mapped_column(Text, nullable=False)
    is_active: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=True, server_default="true"
    )

    reseller: Mapped["Reseller"] = relationship(back_populates="banners")
