#!/usr/bin/env bash
# Скачивает history-*.json.gz с Report Web App (логин по ключу доступа).
# Переменные: REPORT_WEB_APP_URL, REPORT_ACCESS_KEY; опционально OUT_DIR (по умолчанию backups).

set -euo pipefail

: "${REPORT_WEB_APP_URL:?Set REPORT_WEB_APP_URL (e.g. https://report.example.com)}"
: "${REPORT_ACCESS_KEY:?Set REPORT_ACCESS_KEY}"

REPORT_WEB_APP_URL="${REPORT_WEB_APP_URL%/}"
OUT_DIR="${OUT_DIR:-backups}"
COOKIE_JAR="$(mktemp)"

cleanup() {
  rm -f "$COOKIE_JAR"
}
trap cleanup EXIT

mkdir -p "$OUT_DIR"

echo "Logging in to ${REPORT_WEB_APP_URL} ..."
curl -fsS -c "$COOKIE_JAR" -b "$COOKIE_JAR" \
  -X POST "${REPORT_WEB_APP_URL}/auth/access" \
  -d "accessKey=${REPORT_ACCESS_KEY}" \
  -o /dev/null

if ! curl -fsS -b "$COOKIE_JAR" "${REPORT_WEB_APP_URL}/api/backup" | jq -e '.enabled == true' >/dev/null; then
  echo "Login failed or backup API is disabled. Check REPORT_ACCESS_KEY and report.backup.enabled." >&2
  exit 1
fi

echo "Creating backup on server ..."
RESP="$(curl -fsS -b "$COOKIE_JAR" -X POST "${REPORT_WEB_APP_URL}/api/backup")"

if echo "$RESP" | jq -e '.skipped == true' >/dev/null 2>&1; then
  echo "Server skipped new archive: $(echo "$RESP" | jq -r '.message')"
  FILE="$(curl -fsS -b "$COOKIE_JAR" "${REPORT_WEB_APP_URL}/api/backup" | jq -r '.backups[0].fileName')"
else
  FILE="$(echo "$RESP" | jq -r '.backup.fileName')"
fi

if [[ -z "$FILE" || "$FILE" == "null" ]]; then
  echo "No backup file name in API response." >&2
  exit 1
fi

DEST="${OUT_DIR}/${FILE}"
echo "Downloading ${FILE} ..."
curl -fsS -b "$COOKIE_JAR" \
  -o "$DEST" \
  "${REPORT_WEB_APP_URL}/api/backup/${FILE}/download"

SIZE="$(wc -c < "$DEST" | tr -d ' ')"
echo "Saved ${DEST} (${SIZE} bytes)"
