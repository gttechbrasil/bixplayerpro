from datetime import date, datetime

from pydantic import BaseModel, Field, field_validator

from app.schemas.common import ORMModel

MAC_PATTERN = r"^[0-9A-Fa-f]{2}([:\-]?[0-9A-Fa-f]{2}){5}$"
DEFAULT_LICENSE_EXPIRATION = date(2050, 1, 1)


class ResellerDeviceCreate(BaseModel):
    mac_address: str = Field(min_length=12, max_length=17, pattern=MAC_PATTERN)
    client_name: str | None = Field(None, max_length=120)
    playlist_name: str = Field(min_length=1, max_length=120)
    playlist_url: str = Field(min_length=8, max_length=2048)
    license_expires_at: date | None = DEFAULT_LICENSE_EXPIRATION

    @field_validator("client_name")
    @classmethod
    def _strip(cls, v: str | None) -> str | None:
        v = (v or "").strip()
        return v or None


class ResellerDeviceUpdate(BaseModel):
    client_name: str | None = Field(None, max_length=120)
    playlist_name: str = Field(min_length=1, max_length=120)
    playlist_url: str = Field(min_length=8, max_length=2048)
    license_expires_at: date | None = None

    @field_validator("client_name")
    @classmethod
    def _strip(cls, v: str | None) -> str | None:
        v = (v or "").strip()
        return v or None


class ResellerDeviceOut(ORMModel):
    id: int
    mac_address: str
    client_name: str | None
    license_expires_at: date | None
    playlist_name: str | None = None
    playlist_url: str | None = None
    playlist_host: str | None = None
    playlists_count: int = 0
    app_type: str | None
    app_version: str | None
    last_seen_at: datetime | None
    connected: bool = False
    status: str = "active"
    created_at: datetime


class BatchDelete(BaseModel):
    ids: list[int] = Field(min_length=1, max_length=500)


class BatchDeleteResult(BaseModel):
    deleted: int
    message: str
