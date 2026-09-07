"""device diagnostics (M5-013)

Revision ID: c7a1d0e5f3b2
Revises: b78b8ee29ad1
Create Date: 2026-09-07 14:00:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "c7a1d0e5f3b2"
down_revision: str | None = "b78b8ee29ad1"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "device_diagnostics",
        sa.Column("id", sa.BigInteger(), primary_key=True),
        sa.Column(
            "device_id",
            sa.BigInteger(),
            sa.ForeignKey("devices.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("kind", sa.String(length=16), nullable=False, server_default="manual"),
        sa.Column("app_version", sa.String(length=32), nullable=True),
        sa.Column("device_info", sa.JSON(), nullable=True),
        sa.Column("size", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("body", sa.Text(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
    )
    op.create_index(
        "ix_device_diagnostics_device_id", "device_diagnostics", ["device_id"], unique=False
    )


def downgrade() -> None:
    op.drop_index("ix_device_diagnostics_device_id", table_name="device_diagnostics")
    op.drop_table("device_diagnostics")
