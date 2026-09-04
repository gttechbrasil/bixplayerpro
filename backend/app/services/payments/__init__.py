from functools import lru_cache

from app.core.config import get_settings
from app.services.payments.base import PaymentProvider


@lru_cache
def get_provider() -> PaymentProvider:
    settings = get_settings()
    if settings.payment_provider == "fake":
        from app.services.payments.fake import FakeProvider

        return FakeProvider()
    if settings.payment_provider == "mercadopago":
        from app.services.payments.mercadopago import MercadoPagoProvider

        return MercadoPagoProvider(
            access_token=settings.mercadopago_access_token,
            webhook_secret=settings.mercadopago_webhook_secret,
        )
    raise RuntimeError(f"unknown PAYMENT_PROVIDER: {settings.payment_provider}")
