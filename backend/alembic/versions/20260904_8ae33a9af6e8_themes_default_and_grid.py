"""themes default and grid

Revision ID: 8ae33a9af6e8
Revises: f2954886a163
Create Date: 2026-09-04 17:43:41.006286

"""

from collections.abc import Sequence

from alembic import op

revision: str = "8ae33a9af6e8"
down_revision: str | None = "f2954886a163"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # v1 ships two home layouts: "default" and "grid" (replaces the theme_d..theme_8 catalogue)
    op.execute("UPDATE resellers SET theme = 'default' WHERE theme NOT IN ('default', 'grid')")
    op.alter_column("resellers", "theme", server_default="default")


def downgrade() -> None:
    op.alter_column("resellers", "theme", server_default="theme_d")
    op.execute("UPDATE resellers SET theme = 'theme_d' WHERE theme IN ('default', 'grid')")
