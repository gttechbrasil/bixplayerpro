from fastapi import APIRouter

from app.api.v1.reseller import devices, dns

router = APIRouter(prefix="/reseller")
router.include_router(devices.router)
router.include_router(dns.router)
