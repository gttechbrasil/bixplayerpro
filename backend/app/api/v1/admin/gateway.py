"""Read-only view of the payment gateway configuration (secrets live in the .env)."""

from fastapi import APIRouter
from pydantic import BaseModel

from app.core.config import Settings, get_settings
from app.core.deps import CurrentAdmin

router = APIRouter(prefix="/settings/gateway", tags=["admin: settings"])


class GatewayOut(BaseModel):
    provider: str
    access_token_masked: str | None
    access_token_kind: str | None
    webhook_secret_configured: bool
    sandbox_payer_email: str | None
    webhook_url: str
    pix_expiration_minutes: int


def mask(value: str) -> str | None:
    if not value:
        return None
    if len(value) <= 12:
        return value[:2] + "…" + value[-2:]
    return value[:8] + "…" + value[-4:]


def token_kind(value: str) -> str | None:
    if not value:
        return None
    if value.startswith("TEST-"):
        return "teste"
    if value.startswith("APP_USR-"):
        return "produção"
    return "desconhecido"


def gateway_info(settings: Settings) -> GatewayOut:
    return GatewayOut(
        provider=settings.payment_provider,
        access_token_masked=mask(settings.mercadopago_access_token),
        access_token_kind=token_kind(settings.mercadopago_access_token),
        webhook_secret_configured=bool(settings.mercadopago_webhook_secret),
        sandbox_payer_email=settings.mercadopago_test_payer_email or None,
        webhook_url=f"{settings.public_base_url.rstrip('/')}/api/v1/webhooks/mercadopago",
        pix_expiration_minutes=settings.pix_expiration_minutes,
    )


@router.get(
    "", summary="Configuração do gateway de pagamento (mascarada)", response_model=GatewayOut
)
async def get_gateway(_: CurrentAdmin) -> GatewayOut:
    return gateway_info(get_settings())
