# Backend tasks. Requires uv (https://docs.astral.sh/uv/).
# Copy ../deploy/.env.example to ./.env (or export the variables) before dev/migrate.

UV ?= uv

.PHONY: install dev test migrate migration lint format seed

install:
	$(UV) sync --all-groups

dev:
	$(UV) run uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

test:
	$(UV) run pytest --cov=app --cov-report=term-missing --cov-fail-under=80

migrate:
	$(UV) run alembic upgrade head

# usage: make migration m="add foo"
migration:
	$(UV) run alembic revision --autogenerate -m "$(m)"

lint:
	$(UV) run ruff check .
	$(UV) run ruff format --check .

format:
	$(UV) run ruff format .
	$(UV) run ruff check --fix .

seed:
	$(UV) run python -m app.db.seed
