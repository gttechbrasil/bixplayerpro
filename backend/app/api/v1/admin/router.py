from fastapi import APIRouter

from app.api.v1.admin import audit, dashboard, gateway, payments, resellers, settings

router = APIRouter(prefix="/admin")
router.include_router(resellers.router)
router.include_router(settings.router)
router.include_router(gateway.router)
router.include_router(dashboard.router)
router.include_router(payments.router)
router.include_router(audit.router)
