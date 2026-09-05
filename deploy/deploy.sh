#!/usr/bin/env bash
# One-command deploy: push the current branch, then rebuild the stack on the VPS.
#
#   ./deploy/deploy.sh              # push + pull + build + migrate
#   ./deploy/deploy.sh --no-push    # skip the git push (server pulls what is on the remote)
#   ./deploy/deploy.sh --logs       # follow the api/web logs after deploying
#
# Reads deploy/.vps.env for VPS_HOST and connects as `deploy` with deploy/id_deploy.
# Both files are gitignored.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$DIR")"
KEY="$DIR/id_deploy"
ENV_FILE="$DIR/.vps.env"
REMOTE_DIR="/home/deploy/app"
BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD)"

PUSH=1
LOGS=0
for arg in "$@"; do
	case "$arg" in
	--no-push) PUSH=0 ;;
	--logs) LOGS=1 ;;
	*)
		echo "argumento desconhecido: $arg" >&2
		exit 2
		;;
	esac
done

[ -f "$ENV_FILE" ] || {
	echo "faltando $ENV_FILE (VPS_HOST=...)" >&2
	exit 1
}
[ -f "$KEY" ] || {
	echo "faltando a chave $KEY" >&2
	exit 1
}
# shellcheck disable=SC1090
VPS_HOST="$(grep -E '^VPS_HOST=' "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"' \r')"

ssh_run() {
	MSYS_NO_PATHCONV=1 ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o IdentitiesOnly=yes \
		"deploy@$VPS_HOST" "$1"
}

if [ "$PUSH" = 1 ]; then
	echo "==> enviando $BRANCH para o origin"
	git -C "$ROOT" push origin "$BRANCH"
fi

echo "==> atualizando o código no servidor"
ssh_run "cd $REMOTE_DIR && git fetch --quiet origin && git reset --hard origin/$BRANCH && git log -1 --oneline"

echo "==> rebuild e subida dos serviços"
ssh_run "cd $REMOTE_DIR && docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build"

echo "==> migrações"
ssh_run "cd $REMOTE_DIR && docker compose -f deploy/docker-compose.yml --env-file deploy/.env exec -T api alembic upgrade head"

echo "==> limpeza de imagens antigas"
ssh_run "docker image prune -f >/dev/null && echo ok"

echo "==> estado dos serviços"
ssh_run "cd $REMOTE_DIR && docker compose -f deploy/docker-compose.yml --env-file deploy/.env ps"

DOMAIN="$(ssh_run "grep -E '^DOMAIN=' $REMOTE_DIR/deploy/.env | cut -d= -f2-" | tr -d '\r')"
echo "==> healthcheck"
curl -fsS "https://$DOMAIN/api/v1/health" && echo

if [ "$LOGS" = 1 ]; then
	ssh_run "cd $REMOTE_DIR && docker compose -f deploy/docker-compose.yml --env-file deploy/.env logs -f --tail=50 api web"
fi

echo "deploy concluído: https://$DOMAIN"
