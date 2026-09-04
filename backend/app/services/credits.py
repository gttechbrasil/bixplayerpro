"""Credit balance changes. Every change writes a ledger row and an audit entry."""

from sqlalchemy import update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import bad_request
from app.models import CreditLedger, Reseller
from app.services import audit

MSG_INSUFFICIENT = "Créditos insuficientes."


async def adjust_credits(
    db: AsyncSession,
    reseller: Reseller,
    delta: int,
    *,
    reason: str,
    actor_type: str,
    actor_id: int | None,
    note: str | None = None,
    ref: str | None = None,
    ip: str | None = None,
) -> CreditLedger:
    if delta == 0:
        raise bad_request("O ajuste de créditos não pode ser zero.", "zero_delta")

    # Atomic update guarded by the balance; the CHECK constraint is the last line of defence.
    result = await db.execute(
        update(Reseller)
        .where(Reseller.id == reseller.id, Reseller.credits + delta >= 0)
        .values(credits=Reseller.credits + delta)
        .returning(Reseller.credits)
    )
    new_balance = result.scalar_one_or_none()
    if new_balance is None:
        raise bad_request(MSG_INSUFFICIENT, "insufficient_credits")
    reseller.credits = new_balance

    entry = CreditLedger(
        reseller_id=reseller.id,
        delta=delta,
        balance_after=new_balance,
        reason=reason,
        note=note,
        ref=ref,
        actor_type=actor_type,
        actor_id=actor_id,
    )
    db.add(entry)
    await db.flush()
    await audit.record(
        db,
        actor_type=actor_type,
        actor_id=actor_id,
        action="credits.adjust",
        target=f"reseller:{reseller.id}",
        payload={"delta": delta, "balance_after": new_balance, "reason": reason, "note": note},
        ip=ip,
    )
    return entry
