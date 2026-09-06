#!/usr/bin/env bash
# Minimal uptime monitor for the VPS, meant for cron every 5 minutes as the `deploy` user:
#
#   */5 * * * * /home/deploy/healthcheck-alert.sh >> /home/deploy/healthcheck.log 2>&1
#
# Checks https://DOMAIN/api/v1/health (expects "status":"ok"). On failure it alerts once
# (and again when the service recovers) by Telegram and/or e-mail, depending on what is
# configured in /home/deploy/alert.env:
#
#   TELEGRAM_BOT_TOKEN=123456:ABC...   # bot created with @BotFather
#   TELEGRAM_CHAT_ID=-1001234567890    # chat/group that receives the alerts
#   ALERT_EMAIL=ops@exemplo.com        # needs a working `mail` command (msmtp/mailutils)
#
# Without alert.env it only logs. State lives in /home/deploy/.healthcheck.state so a long
# outage does not produce one message every 5 minutes.
set -uo pipefail

APP=/home/deploy/app
STATE=/home/deploy/.healthcheck.state
ALERT_ENV=/home/deploy/alert.env
DOMAIN="$(grep -E '^DOMAIN=' "$APP/deploy/.env" | cut -d= -f2- | tr -d '"'"'"' \r')"
URL="https://${DOMAIN}/api/v1/health"

[ -f "$ALERT_ENV" ] && { set -a; . "$ALERT_ENV"; set +a; }

body="$(curl -fsS --max-time 15 "$URL" 2>&1)"
rc=$?
if [ $rc -eq 0 ] && echo "$body" | grep -q '"status":"ok"'; then
	status=ok
else
	status=down
fi
previous="$(cat "$STATE" 2>/dev/null || echo ok)"
echo "$status" > "$STATE"
now="$(date -Is)"

notify() {
	local text="$1"
	echo "$now $text"
	if [ -n "${TELEGRAM_BOT_TOKEN:-}" ] && [ -n "${TELEGRAM_CHAT_ID:-}" ]; then
		curl -fsS --max-time 15 -o /dev/null \
			--data-urlencode "chat_id=${TELEGRAM_CHAT_ID}" \
			--data-urlencode "text=${text}" \
			"https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
			|| echo "$now telegram send failed"
	fi
	if [ -n "${ALERT_EMAIL:-}" ] && command -v mail >/dev/null 2>&1; then
		printf '%s\n' "$text" | mail -s "[${DOMAIN}] ${text%%:*}" "$ALERT_EMAIL" || echo "$now mail failed"
	fi
}

if [ "$status" = down ] && [ "$previous" != down ]; then
	containers="$(cd "$APP" && docker compose -f deploy/docker-compose.yml --env-file deploy/.env ps --format '{{.Name}} {{.Status}}' 2>&1 | tr '\n' '; ')"
	notify "ALERTA ${DOMAIN}: health falhou (curl rc=${rc}) ${body:0:120} | ${containers}"
elif [ "$status" = ok ] && [ "$previous" = down ]; then
	notify "OK ${DOMAIN}: serviço voltou"
fi
exit 0
