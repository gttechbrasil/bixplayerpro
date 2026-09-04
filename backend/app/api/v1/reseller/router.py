from fastapi import APIRouter

from app.api.v1.reseller import branding, devices, dns, profile

router = APIRouter(prefix="/reseller")
router.include_router(devices.router)
router.include_router(dns.router)
router.include_router(branding.router)
router.include_router(profile.router)
