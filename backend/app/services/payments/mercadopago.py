"""Mercado Pago Pix provider (Payments API).

Docs: https://www.mercadopago.com.br/developers/pt/docs/checkout-api/integration-configuration/integrate-with-pix
Webhook signature: https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks
"""

import hashlib
import hmac
import logging
import uuid
from datetime import UTC, datetime
from decimal import Decimal
from typing import Any

import httpx

from app.services.payments.base import PaymentStatus, PixCharge, ProviderError, ProviderPayment

log = logging.getLogger(__name__)

API_BASE = "https://api.mercadopago.com"

# Mercado Pago status -> platform status
_STATUS_MAP: dict[str, PaymentStatus] = {
    "pending": "pending",
    "in_process": "pending",
    "in_mediation": "pending",
    "authorized": "pending",
    "approved": "approved",
    "cancelled": "cancelled",
    "rejected": "cancelled",
    "refunded": "cancelled",
    "charged_back": "cancelled",
}


def _parse_dt(value: str | None) -> datetime | None:
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


class MercadoPagoProvider:
    name = "mercadopago"

    def __init__(
        self,
        access_token: str,
        webhook_secret: str = "",
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        if not access_token:
            raise ProviderError("MERCADOPAGO_ACCESS_TOKEN não configurado.")
        self._token = access_token
        self._secret = webhook_secret
        self._transport = transport

    def _client(self) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url=API_BASE,
            headers={"Authorization": f"Bearer {self._token}"},
            timeout=20.0,
            transport=self._transport,
        )

    async def create_pix(
        self,
        *,
        amount: Decimal,
        description: str,
        external_reference: str,
        payer_email: str,
        expires_at: datetime,
    ) -> PixCharge:
        payload: dict[str, Any] = {
            "transaction_amount": float(amount),
            "description": description,
            "payment_method_id": "pix",
            "external_reference": external_reference,
            "payer": {"email": payer_email},
            "date_of_expiration": expires_at.astimezone(UTC).strftime(
                "%Y-%m-%dT%H:%M:%S.000-00:00"
            ),
        }
        async with self._client() as client:
            try:
                resp = await client.post(
                    "/v1/payments",
                    json=payload,
                    headers={"X-Idempotency-Key": str(uuid.uuid4())},
                )
            except httpx.HTTPError as exc:
                raise ProviderError(f"Falha de conexão com o Mercado Pago: {exc}") from exc
        if resp.status_code >= 400:
            log.warning("mercadopago create_pix failed: %s %s", resp.status_code, resp.text[:500])
            raise ProviderError(f"Mercado Pago recusou a cobrança ({resp.status_code}).")
        data = resp.json()
        tx = (data.get("point_of_interaction") or {}).get("transaction_data") or {}
        if not tx.get("qr_code"):
            raise ProviderError("Mercado Pago não devolveu o código Pix.")
        return PixCharge(
            provider_id=str(data["id"]),
            qr_code=tx["qr_code"],
            qr_base64=tx.get("qr_code_base64", ""),
            expires_at=_parse_dt(data.get("date_of_expiration")) or expires_at,
            status=_STATUS_MAP.get(data.get("status", "pending"), "pending"),
        )

    async def get_payment(self, provider_id: str) -> ProviderPayment:
        async with self._client() as client:
            try:
                resp = await client.get(f"/v1/payments/{provider_id}")
            except httpx.HTTPError as exc:
                raise ProviderError(f"Falha de conexão com o Mercado Pago: {exc}") from exc
        if resp.status_code == 404:
            return ProviderPayment(provider_id, "cancelled")
        if resp.status_code >= 400:
            raise ProviderError(f"Mercado Pago indisponível ({resp.status_code}).")
        data = resp.json()
        status = _STATUS_MAP.get(data.get("status", "pending"), "pending")
        if status == "pending":
            expires = _parse_dt(data.get("date_of_expiration"))
            if expires and expires < datetime.now(UTC):
                status = "expired"
        amount = data.get("transaction_amount")
        return ProviderPayment(
            provider_id=str(data["id"]),
            status=status,
            paid_at=_parse_dt(data.get("date_approved")),
            amount=Decimal(str(amount)) if amount is not None else None,
        )

    def verify_webhook(self, *, headers: dict[str, str], data_id: str) -> bool:
        """Validates the `x-signature` header (HMAC-SHA256 over the documented manifest).
        Without a configured secret the signature is not checked; the caller must then
        confirm the payment through `get_payment`, which it always does anyway."""
        if not self._secret:
            return True
        lower = {k.lower(): v for k, v in headers.items()}
        signature = lower.get("x-signature", "")
        request_id = lower.get("x-request-id", "")
        parts = dict(p.strip().split("=", 1) for p in signature.split(",") if "=" in p)
        ts, v1 = parts.get("ts"), parts.get("v1")
        if not ts or not v1:
            return False
        manifest = f"id:{data_id.lower()};request-id:{request_id};ts:{ts};"
        expected = hmac.new(self._secret.encode(), manifest.encode(), hashlib.sha256).hexdigest()
        return hmac.compare_digest(expected, v1)
