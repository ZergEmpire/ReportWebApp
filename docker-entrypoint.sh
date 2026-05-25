#!/bin/sh
set -e
mkdir -p /data/backups
# Amvera монтирует том в /data; даём право записи пользователю приложения
if [ "$(id -u)" = "0" ]; then
  chown -R appuser:appuser /data 2>/dev/null || true
  exec runuser -u appuser -- java -jar /app/app.jar "$@"
fi
exec java -jar /app/app.jar "$@"
