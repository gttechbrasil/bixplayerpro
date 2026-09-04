from fastapi import APIRouter, Request

from app.core.deps import CurrentReseller, DbSession, get_client_ip
from app.core.exceptions import bad_request
from app.core.security import hash_password, verify_password
from app.schemas.common import Message
from app.schemas.reseller import PasswordChange, ProfileOut
from app.services import audit

router = APIRouter(prefix="/profile", tags=["reseller: profile"])

MSG_WRONG_PASSWORD = "Senha atual incorreta."


@router.get("", summary="Perfil da revenda", response_model=ProfileOut)
async def get_profile(reseller: CurrentReseller) -> ProfileOut:
    return ProfileOut.model_validate(reseller)


@router.put("/password", summary="Troca a própria senha", response_model=Message)
async def change_password(
    body: PasswordChange, reseller: CurrentReseller, db: DbSession, request: Request
) -> Message:
    if not verify_password(body.current_password, reseller.password_hash):
        raise bad_request(MSG_WRONG_PASSWORD, "wrong_password")
    reseller.password_hash = hash_password(body.new_password)
    await audit.record(
        db,
        actor_type="reseller",
        actor_id=reseller.id,
        action="reseller.password_change",
        target=f"reseller:{reseller.id}",
        ip=get_client_ip(request),
    )
    await db.commit()
    return Message(message="Senha alterada.")
