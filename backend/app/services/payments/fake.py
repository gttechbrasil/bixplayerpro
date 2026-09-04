"""In-memory provider for development and tests. Payments start pending and are
approved through `FakeProvider.approve(provider_id)`."""

import secrets
from datetime import UTC, datetime
from decimal import Decimal

from app.services.payments.base import PixCharge, ProviderPayment


class FakeProvider:
    name = "fake"

    def __init__(self) -> None:
        self.payments: dict[str, ProviderPayment] = {}

    async def create_pix(
        self,
        *,
        amount: Decimal,
        description: str,
        external_reference: str,
        payer_email: str,
        expires_at: datetime,
    ) -> PixCharge:
        provider_id = f"fake-{secrets.token_hex(6)}"
        self.payments[provider_id] = ProviderPayment(provider_id, "pending", None, amount)
        return PixCharge(
            provider_id=provider_id,
            qr_code=f"00020126FAKE{provider_id}5204000053039865802BR",
            qr_base64="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
            expires_at=expires_at,
        )

    async def get_payment(self, provider_id: str) -> ProviderPayment:
        return self.payments.get(provider_id) or ProviderPayment(provider_id, "cancelled")

    def verify_webhook(self, *, headers: dict[str, str], data_id: str) -> bool:
        return True

    def approve(self, provider_id: str) -> None:
        current = self.payments[provider_id]
        self.payments[provider_id] = ProviderPayment(
            provider_id, "approved", datetime.now(UTC), current.amount
        )

    def set_status(self, provider_id: str, status: str) -> None:
        current = self.payments[provider_id]
        self.payments[provider_id] = ProviderPayment(provider_id, status, None, current.amount)  # type: ignore[arg-type]
