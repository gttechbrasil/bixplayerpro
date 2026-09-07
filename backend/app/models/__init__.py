"""Import all models so Alembic and SQLAlchemy see the full metadata."""

from app.db.base import Base
from app.models.admin import Admin
from app.models.audit import AuditLog
from app.models.banner import Banner
from app.models.device import Device
from app.models.diagnostic import DeviceDiagnostic
from app.models.ledger import CreditLedger
from app.models.payment import Payment
from app.models.playlist import Playlist
from app.models.reseller import Reseller
from app.models.setting import Setting

__all__ = [
    "Admin",
    "AuditLog",
    "Banner",
    "Base",
    "CreditLedger",
    "Device",
    "DeviceDiagnostic",
    "Payment",
    "Playlist",
    "Reseller",
    "Setting",
]
