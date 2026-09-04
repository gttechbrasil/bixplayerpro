from fastapi import APIRouter

from app.api.v1.reseller import devices

router = APIRouter(prefix="/reseller")
router.include_router(devices.router)
