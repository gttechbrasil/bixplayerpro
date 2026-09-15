"""resellers created from now on start on the side-menu layout (F2-006)

Existing resellers keep whatever they chose; only the column default changes.

Revision ID: a4e71c92b8d5
Revises: c7a1d0e5f3b2
Create Date: 2026-09-16 09:00:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "a4e71c92b8d5"
down_revision: str | None = "c7a1d0e5f3b2"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.alter_column(
        "resellers",
        "theme",
        existing_type=sa.String(length=16),
        existing_nullable=False,
        server_default="rail",
    )


def downgrade() -> None:
    op.alter_column(
        "resellers",
        "theme",
        existing_type=sa.String(length=16),
        existing_nullable=False,
        server_default="default",
    )
