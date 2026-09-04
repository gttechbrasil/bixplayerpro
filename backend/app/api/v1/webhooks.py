"""Inbound webhooks from payment providers."""

import logging
from typing import Any

from fastapi import APIRouter, Request
from pydantic import BaseModel

from app.core.deps import DbSession
from app.core.exceptions import forbidden
from app.services.billing import find_by_provider_id, sync_payment
from app.services.payments import get_provider

log = logging.getLogger(__name__)

router = APIRouter(prefix="/webhooks", tags=["webhooks"])


class WebhookResult(BaseModel):
    processed: bool
    status: str | None = None


@router.post(
    "/mercadopago", summary="Notificação de pagamento do Mercado Pago", response_model=WebhookResult
)
async def mercadopago(request: Request, db: DbSession) -> WebhookResult:
    """Mercado Pago sends `{type: "payment", data: {id}}` (plus `data.id` in the query
    string). The signature is validated when a secret is configured and the payment is
    always re-fetched from the provider before anything is approved."""
    body: dict[str, Any] = {}
    try:
        body = await request.json()
    except Exception:  # noqa: BLE001 - some notifications have an empty body
        body = {}
    data_id = str(
        request.query_params.get("data.id")
        or request.query_params.get("id")
        or (body.get("data") or {}).get("id")
        or ""
    )
    event_type = str(body.get("type") or request.query_params.get("type") or "")
    if not data_id or (event_type and event_type != "payment"):
        return WebhookResult(processed=False)

    provider = get_provider()
    if provider.name != "mercadopago":
        return WebhookResult(processed=False)
    if not provider.verify_webhook(headers=dict(request.headers), data_id=data_id):
        raise forbidden("Assinatura do webhook inválida.", "invalid_signature")

    payment = await find_by_provider_id(db, "mercadopago", data_id)
    if payment is None:
        log.info("webhook for unknown payment %s", data_id)
        return WebhookResult(processed=False)
    payment = await sync_payment(db, payment, source="webhook")
    await db.commit()
    return WebhookResult(processed=True, status=payment.status)
