#!/usr/bin/env bash
# Publish the stock backgrounds (F2-008) and banners (F2-012) offered in the panel.
#
#   ./deploy/push-backgrounds.sh
#
# The uploads directory is a Docker volume, not part of the repo, so `deploy.sh` does not carry
# these images. Run this after the first deploy and whenever the art changes: drop the PNGs in
# docs/brand, map them in backend/scripts/make_backgrounds.py, run it, then run this. The panel
# points at fixed URLs, so every reseller already using one of them gets the new art at once.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$DIR")"
# No WSL o /mnt/c monta tudo como 0444 e o ssh recusa a chave; copie-a para o home do Linux
# (chmod 600) e aponte DEPLOY_KEY para lá, ou rode este script pelo Git Bash do Windows.
KEY="${DEPLOY_KEY:-$DIR/id_deploy}"
ENV_FILE="$DIR/.vps.env"
REMOTE_DIR="/home/deploy/app"
COMPOSE="docker compose -f deploy/docker-compose.yml --env-file deploy/.env"

VPS_HOST="$(grep -E '^VPS_HOST=' "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"' \r')"

ssh_run() {
	MSYS_NO_PATHCONV=1 ssh -n -i "$KEY" -o StrictHostKeyChecking=accept-new -o IdentitiesOnly=yes \
		"deploy@$VPS_HOST" "$1"
}

# folder -> files
publish() {
	local folder="$1"; shift
	local src="$ROOT/backend/uploads/$folder"
	for name in "$@"; do
		[ -f "$src/$name.jpg" ] || {
			echo "faltando $src/$name.jpg (rode: py -3.12 backend/scripts/make_backgrounds.py)" >&2
			exit 1
		}
	done
	echo "==> enviando $folder"
	ssh_run "mkdir -p $REMOTE_DIR/deploy/$folder"
	for name in "$@"; do
		MSYS_NO_PATHCONV=1 scp -i "$KEY" -o IdentitiesOnly=yes "$src/$name.jpg" \
			"deploy@$VPS_HOST:$REMOTE_DIR/deploy/$folder/"
	done
	echo "==> copiando $folder para o volume de uploads"
	ssh_run "cd $REMOTE_DIR && $COMPOSE exec -T api mkdir -p /app/uploads/$folder"
	for name in "$@"; do
		ssh_run "cd $REMOTE_DIR && $COMPOSE cp deploy/$folder/$name.jpg api:/app/uploads/$folder/$name.jpg"
	done
	ssh_run "cd $REMOTE_DIR && $COMPOSE exec -T api ls -l /app/uploads/$folder"
}

publish backgrounds bg1 bg2 bg3
publish banners b1 b2 b3

echo "pronto: https://bixplayer.pro/uploads/backgrounds/bg1.jpg e /uploads/banners/b1.jpg"
