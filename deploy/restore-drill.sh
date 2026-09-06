#!/usr/bin/env bash
# Restore drill: load the most recent pg_dump into a throwaway database `restore_test`,
# compare table counts with the live database, then drop the throwaway database.
# The production database is only read.
set -euo pipefail

cd /home/deploy/app
COMPOSE="docker compose -f deploy/docker-compose.yml --env-file deploy/.env"
LATEST=$(ls -t /home/deploy/backups/db-*.sql.gz | head -1)
echo "dump: $LATEST ($(du -h "$LATEST" | cut -f1), $(date -r "$LATEST" -Is))"
gunzip -t "$LATEST" && echo "gzip integro"

$COMPOSE exec -T db sh -c 'psql -q -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS restore_test" -c "CREATE DATABASE restore_test"'
gunzip -c "$LATEST" | $COMPOSE exec -T db sh -c 'psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d restore_test'
echo "restore ok"

COUNTS='select (select count(*) from admins) admins, (select count(*) from resellers) resellers, (select count(*) from devices) devices, (select count(*) from playlists) playlists, (select count(*) from payments) payments, (select count(*) from audit_log) audit, (select count(*) from alembic_version) alembic'
echo "live:     $($COMPOSE exec -T db sh -c "psql -At -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"$COUNTS\"")"
echo "restored: $($COMPOSE exec -T db sh -c "psql -At -U \"\$POSTGRES_USER\" -d restore_test -c \"$COUNTS\"")"
echo "alembic restored: $($COMPOSE exec -T db sh -c 'psql -At -U "$POSTGRES_USER" -d restore_test -c "select version_num from alembic_version"')"
$COMPOSE exec -T db sh -c 'psql -q -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE restore_test"'
echo "restore_test dropped"
