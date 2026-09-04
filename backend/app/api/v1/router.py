from fastapi import APIRouter

from app.api.v1 import auth, device, health
from app.api.v1.admin.router import router as admin_router
from app.api.v1.reseller.router import router as reseller_router

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(health.router)
api_router.include_router(auth.router)
api_router.include_router(device.router)
api_router.include_router(admin_router)
api_router.include_router(reseller_router)
