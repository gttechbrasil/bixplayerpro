from datetime import date
from typing import Literal

from pydantic import BaseModel, Field

from app.schemas.common import ORMModel

DeviceStatus = Literal["active", "expired", "unregistered"]


class DeviceRegisterRequest(BaseModel):
    device_id: str = Field(
        min_length=4,
        max_length=256,
        description="Identificador estável do aparelho (ex.: ANDROID_ID)",
    )
    app_type: Literal["tv", "mobile"] = "tv"
    app_version: str = Field(default="", max_length=32)


class DeviceRegisterResponse(BaseModel):
    mac_address: str
    token: str


class PlaylistOut(ORMModel):
    id: int
    name: str
    url: str
    type: str
    is_protected: bool


class BannerOut(ORMModel):
    id: int
    title: str
    url: str


class DeviceConfig(BaseModel):
    registered: bool
    mac_address: str
    status: DeviceStatus
    client_name: str | None
    license_expires_at: date | None
    playlists: list[PlaylistOut]
    theme: str
    logo_url: str | None
    bg_url: str | None
    qr_content: str | None
    banners: list[BannerOut]
    auto_ads: bool
    pin: str
    min_app_version: str
    apk_url: str
    platform_name: str


class PlaylistCreate(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    url: str = Field(min_length=8, max_length=2048)
    is_protected: bool = False
