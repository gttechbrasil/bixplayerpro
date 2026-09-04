from typing import TYPE_CHECKING

from sqlalchemy import BigInteger, Boolean, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin

if TYPE_CHECKING:
    from app.models.device import Device

PLAYLIST_TYPES = ("xtream", "m3u")


class Playlist(TimestampMixin, Base):
    __tablename__ = "playlists"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    device_id: Mapped[int] = mapped_column(
        BigInteger, ForeignKey("devices.id", ondelete="CASCADE"), nullable=False, index=True
    )
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    url: Mapped[str] = mapped_column(Text, nullable=False)
    type: Mapped[str] = mapped_column(String(8), nullable=False)
    host: Mapped[str | None] = mapped_column(String(255), nullable=True, index=True)
    username: Mapped[str | None] = mapped_column(String(255), nullable=True)
    password_enc: Mapped[str | None] = mapped_column(Text, nullable=True)
    is_protected: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default="false"
    )
    position: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")

    device: Mapped["Device"] = relationship(back_populates="playlists")
