from datetime import date, datetime
from decimal import Decimal
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator

from app.models.reseller import THEMES
from app.schemas.common import ORMModel

Theme = Literal["default", "grid"]


# ---- resellers ---------------------------------------------------------------
class ResellerCreate(BaseModel):
    username: str = Field(min_length=3, max_length=64, pattern=r"^[a-zA-Z0-9_.-]+$")
    name: str = Field(min_length=1, max_length=120)
    password: str = Field(min_length=6, max_length=128)
    credits: int = Field(0, ge=0)
    expires_at: date | None = None


class ResellerUpdate(BaseModel):
    name: str | None = Field(None, min_length=1, max_length=120)
    username: str | None = Field(None, min_length=3, max_length=64, pattern=r"^[a-zA-Z0-9_.-]+$")
    theme: Theme | None = None
    logo_url: str | None = Field(None, max_length=2048)
    bg_url: str | None = Field(None, max_length=2048)
    qr_content: str | None = Field(None, max_length=2048)
    auto_ads: bool | None = None

    @field_validator("theme")
    @classmethod
    def _theme(cls, v: str | None) -> str | None:
        if v is not None and v not in THEMES:
            raise ValueError("tema inválido")
        return v


class ResellerOut(ORMModel):
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
    created_at: datetime
    updated_at: datetime
    devices_count: int = 0


class BlockUpdate(BaseModel):
    is_blocked: bool


class PasswordReset(BaseModel):
    password: str = Field(min_length=6, max_length=128)


class CreditAdjust(BaseModel):
    delta: int = Field(description="Positivo adiciona, negativo remove")
    note: str = Field(min_length=3, max_length=500, description="Motivo do ajuste")


class ExpirationUpdate(BaseModel):
    expires_at: date | None = Field(description="null = sem vencimento")


class LedgerOut(ORMModel):
    id: int
    reseller_id: int | None
    delta: int
    balance_after: int
    reason: str
    note: str | None
    ref: str | None
    actor_type: str
    actor_id: int | None
    created_at: datetime


# ---- settings ----------------------------------------------------------------
class Package(BaseModel):
    months: int = Field(ge=1, le=60)
    price: Decimal = Field(gt=0, max_digits=10, decimal_places=2)


class SettingsOut(BaseModel):
    credits_enabled: bool
    monthly_price: Decimal
    packages: list[Package]
    min_app_version: str
    apk_url: str
    platform_name: str


class SettingsUpdate(BaseModel):
    credits_enabled: bool | None = None
    monthly_price: Decimal | None = Field(None, gt=0, max_digits=10, decimal_places=2)
    packages: list[Package] | None = None
    min_app_version: str | None = Field(None, max_length=32, pattern=r"^\d+(\.\d+){0,3}$")
    apk_url: str | None = Field(None, max_length=2048)
    platform_name: str | None = Field(None, min_length=1, max_length=80)


# ---- dashboard / listings ----------------------------------------------------
class DashboardOut(BaseModel):
    resellers_total: int
    resellers_active: int
    resellers_blocked: int
    resellers_expired: int
    devices_total: int
    devices_registered: int
    devices_active: int
    devices_seen_24h: int
    payments_month_count: int
    payments_month_amount: Decimal
    payments_pending: int


class PaymentOut(ORMModel):
    id: int
    reseller_id: int | None
    reseller_username: str | None = None
    provider: str
    provider_id: str | None
    months: int
    amount: Decimal
    status: str
    paid_at: datetime | None
    previous_expires_at: date | None
    new_expires_at: date | None
    created_at: datetime


class AuditOut(ORMModel):
    id: int
    actor_type: str
    actor_id: int | None
    action: str
    target: str | None
    payload: dict[str, Any] | None
    ip: str | None
    created_at: datetime
