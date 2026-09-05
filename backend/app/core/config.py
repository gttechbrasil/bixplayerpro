"""Application settings loaded from environment variables / .env file."""

from functools import lru_cache
from typing import Literal

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    app_env: Literal["development", "test", "production"] = "development"
    log_level: str = "info"
    platform_name: str = "Plataforma"
    public_base_url: str = "http://localhost:5173"

    database_url: str = "postgresql+asyncpg://iptv:iptv@localhost:5432/iptv"
    redis_url: str = "redis://localhost:6379/0"

    secret_key: str = Field(default="dev-secret-change-me-dev-secret-change-me", min_length=32)
    fernet_key: str = ""
    jwt_expire_minutes: int = 720
    jwt_algorithm: str = "HS256"
    cookie_secure: bool = False
    cookie_name: str = "access_token"
    csrf_cookie_name: str = "csrf_token"
    csrf_header_name: str = "X-CSRF-Token"
    cors_origins: list[str] = []

    admin_username: str = "admin"
    admin_password: str = "admin"

    mac_prefix: str = "02:50:50"
    upload_dir: str = "./uploads"
    login_rate_limit: int = 10
    login_rate_window: int = 300

    payment_provider: Literal["mercadopago", "fake"] = "mercadopago"
    pix_expiration_minutes: int = 30
    mercadopago_access_token: str = ""
    mercadopago_webhook_secret: str = ""
    # Sandbox only: e-mail of a Mercado Pago *test buyer*. When set it is used as payer.email
    # instead of the synthetic reseller address. Leave empty in production.
    mercadopago_test_payer_email: str = ""

    @field_validator("cors_origins", mode="before")
    @classmethod
    def _split_origins(cls, value: object) -> object:
        if isinstance(value, str):
            return [o.strip() for o in value.split(",") if o.strip()]
        return value

    @field_validator("mac_prefix")
    @classmethod
    def _validate_prefix(cls, value: str) -> str:
        parts = value.upper().split(":")
        if len(parts) != 3 or any(len(p) != 2 for p in parts):
            raise ValueError("MAC_PREFIX must look like XX:XX:XX")
        for p in parts:
            int(p, 16)
        return ":".join(parts)

    @property
    def is_production(self) -> bool:
        return self.app_env == "production"


@lru_cache
def get_settings() -> Settings:
    return Settings()
