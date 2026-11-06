"""reseller CPF, optional: filled in by the admin next to the login data

Revision ID: d3f1a7c04b92
Revises: a4e71c92b8d5
Create Date: 2026-11-05 20:00:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "d3f1a7c04b92"
down_revision: str | None = "a4e71c92b8d5"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("resellers", sa.Column("cpf", sa.String(length=11), nullable=True))


def downgrade() -> None:
    op.drop_column("resellers", "cpf")
