#!/usr/bin/env bash
# Dump lógico do Postgres + cópia do volume de uploads. Retenção de 7 dias.
# As credenciais do banco vêm do ambiente do próprio container, não do .env
# (o .env tem valores com espaço, como PLATFORM_NAME, que quebram `source`).
set -euo pipefail
APP=/home/deploy/app
OUT=/home/deploy/backups
COMPOSE=(docker compose -f "$APP/deploy/docker-compose.yml" --env-file "$APP/deploy/.env")
STAMP=$(date +%F-%H%M)

mkdir -p "$OUT"
"${COMPOSE[@]}" exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' \
	| gzip > "$OUT/db-$STAMP.sql.gz"

if [ ! -s "$OUT/db-$STAMP.sql.gz" ]; then
	echo "$(date -Is) ERRO: dump vazio" >&2
	rm -f "$OUT/db-$STAMP.sql.gz"
	exit 1
fi

docker run --rm -v iptv-platform_uploads:/data:ro -v "$OUT":/backup alpine \
	tar czf "/backup/uploads-$STAMP.tgz" -C /data . 2>/dev/null || true

find "$OUT" -name 'db-*.sql.gz' -mtime +7 -delete
find "$OUT" -name 'uploads-*.tgz' -mtime +7 -delete

echo "$(date -Is) backup ok: db-$STAMP.sql.gz ($(du -h "$OUT/db-$STAMP.sql.gz" | cut -f1))"
