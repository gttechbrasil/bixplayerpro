"""Import all models so Alembic and SQLAlchemy see the full metadata."""

from app.db.base import Base

__all__ = ["Base"]
