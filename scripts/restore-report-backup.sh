#!/usr/bin/env bash
# Загружает history-*.json.gz на стенд и восстанавливает историю (после передеплоя).
# Переменные: REPORT_WEB_APP_URL, REPORT_ACCESS_KEY, BACKUP_FILE (путь к .json.gz).

set -euo pipefail

: "${REPORT_WEB_APP_URL:?Set REPORT_WEB_APP_URL}"
: "${REPORT_ACCESS_KEY:?Set REPORT_ACCESS_KEY}"
: "${BACKUP_FILE:?Set BACKUP_FILE (path to history-*.json.gz)}"

REPORT_WEB_APP_URL="${REPORT_WEB_APP_URL%/}"
COOKIE_JAR="$(mktemp)"

cleanup() {
  rm -f "$COOKIE_JAR"
}
trap cleanup EXIT

if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "File not found: ${BACKUP_FILE}" >&2
  exit 1
fi

echo "Logging in to ${REPORT_WEB_APP_URL} ..."
curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" \
  -X POST "${REPORT_WEB_APP_URL}/auth/access" \
  -d "accessKey=${REPORT_ACCESS_KEY}" \
  -o /dev/null

echo "Uploading and restoring ${BACKUP_FILE} ..."
RESP="$(curl -fsS -b "$COOKIE_JAR" \
  -F "file=@${BACKUP_FILE}" \
  "${REPORT_WEB_APP_URL}/api/backup/upload?restore=true")"

echo "$RESP" | jq .
echo "Restore finished. Refresh Dashboard on ${REPORT_WEB_APP_URL}"
