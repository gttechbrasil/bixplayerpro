#!/usr/bin/env bash
# Publish the stock backgrounds offered in the panel (F2-008).
#
#   ./deploy/push-backgrounds.sh
#
# The uploads directory is a Docker volume, not part of the repo, so `deploy.sh` does not carry
# these images. Run this once after the first deploy, and again whenever the art changes: drop the
# new files over backend/uploads/backgrounds/bg1..3.jpg and run it. The panel points at fixed URLs,
# so every reseller already using one of them gets the new art without touching their settings.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$DIR")"
# No WSL o /mnt/c monta tudo como 0444 e o ssh recusa a chave; copie-a para o home do Linux
# (chmod 600) e aponte DEPLOY_KEY para lá, ou rode este script pelo Git Bash do Windows.
KEY="${DEPLOY_KEY:-$DIR/id_deploy}"
ENV_FILE="$DIR/.vps.env"
REMOTE_DIR="/home/deploy/app"
SRC="$ROOT/backend/uploads/backgrounds"
COMPOSE="docker compose -f deploy/docker-compose.yml --env-file deploy/.env"

VPS_HOST="$(grep -E '^VPS_HOST=' "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"' \r')"

ssh_run() {
	MSYS_NO_PATHCONV=1 ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o IdentitiesOnly=yes \
		"deploy@$VPS_HOST" "$1"
}

for name in bg1 bg2 bg3; do
	[ -f "$SRC/$name.jpg" ] || {
		echo "faltando $SRC/$name.jpg (rode: py -3.12 backend/scripts/make_backgrounds.py)" >&2
		exit 1
	}
done

echo "==> enviando as imagens"
ssh_run "mkdir -p $REMOTE_DIR/deploy/backgrounds"
MSYS_NO_PATHCONV=1 scp -i "$KEY" -o IdentitiesOnly=yes "$SRC"/bg[123].jpg \
	"deploy@$VPS_HOST:$REMOTE_DIR/deploy/backgrounds/"

echo "==> copiando para o volume de uploads"
ssh_run "cd $REMOTE_DIR && $COMPOSE exec -T api mkdir -p /app/uploads/backgrounds"
for name in bg1 bg2 bg3; do
	ssh_run "cd $REMOTE_DIR && $COMPOSE cp deploy/backgrounds/$name.jpg api:/app/uploads/backgrounds/$name.jpg"
done
ssh_run "cd $REMOTE_DIR && $COMPOSE exec -T api ls -l /app/uploads/backgrounds"

echo "pronto: https://bixplayer.pro/uploads/backgrounds/bg1.jpg"
