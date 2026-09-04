from fastapi import APIRouter

from app.api.v1.reseller import billing, branding, devices, dns, profile

router = APIRouter(prefix="/reseller")
router.include_router(devices.router)
router.include_router(dns.router)
router.include_router(branding.router)
router.include_router(profile.router)
router.include_router(billing.router)
