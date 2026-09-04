"""Payment provider interface. Concrete providers live next to this module and are
selected by the PAYMENT_PROVIDER setting (see `get_provider`)."""

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from typing import Literal, Protocol

PaymentStatus = Literal["pending", "approved", "cancelled", "expired"]


@dataclass(frozen=True)
class PixCharge:
    provider_id: str
    qr_code: str  # Pix "copia e cola"
    qr_base64: str  # PNG image, base64 without data: prefix
    expires_at: datetime
    status: PaymentStatus = "pending"


@dataclass(frozen=True)
class ProviderPayment:
    provider_id: str
    status: PaymentStatus
    paid_at: datetime | None = None
    amount: Decimal | None = None


class ProviderError(Exception):
    """Raised when the provider rejects a request or is unreachable."""


class PaymentProvider(Protocol):
    name: str

    async def create_pix(
        self,
        *,
        amount: Decimal,
        description: str,
        external_reference: str,
        payer_email: str,
        expires_at: datetime,
    ) -> PixCharge: ...

    async def get_payment(self, provider_id: str) -> ProviderPayment: ...

    def verify_webhook(self, *, headers: dict[str, str], data_id: str) -> bool: ...
