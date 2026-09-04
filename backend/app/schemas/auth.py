from datetime import date

from pydantic import BaseModel, Field

from app.schemas.common import ORMModel


class LoginRequest(BaseModel):
    username: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=1, max_length=128)


class AdminOut(ORMModel):
    id: int
    username: str


class ResellerMe(ORMModel):
    id: int
    username: str
    name: str
    credits: int
    expires_at: date | None
    is_blocked: bool
    logo_url: str | None
    bg_url: str | None
    qr_content: str | None
    theme: str
    auto_ads: bool


class PlatformInfo(BaseModel):
    name: str
    credits_enabled: bool


class AdminLoginResponse(BaseModel):
    role: str = "admin"
    user: AdminOut
    csrf_token: str
    platform: PlatformInfo


class ResellerLoginResponse(BaseModel):
    role: str = "reseller"
    user: ResellerMe
    csrf_token: str
    platform: PlatformInfo


class MeResponse(BaseModel):
    role: str
    user: AdminOut | ResellerMe
    platform: PlatformInfo
