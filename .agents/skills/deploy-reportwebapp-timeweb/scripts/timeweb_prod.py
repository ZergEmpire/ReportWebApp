#!/usr/bin/env python3
"""Бэкап/восстановление Report Web App (прод Timeweb) без зависимости от jq.

Секреты читаются из .deploy/vps.env (REPORT_AUTH_INITIAL_ACCESS_KEY),
URL — REPORT_WEB_APP_URL или дефолт прода.

Команды:
  python timeweb_prod.py backup                 # создать архив на сервере, скачать, валидировать
  python timeweb_prod.py restore <file.json.gz> # загрузить и восстановить
  python timeweb_prod.py status                 # сводка: build-id + число прогонов
"""
import gzip
import json
import os
import re
import sys
import urllib.request
import urllib.parse
import http.cookiejar
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
DEFAULT_URL = "https://zergempire-reportwebapp-b028.twc1.net"


def load_env():
    url = os.environ.get("REPORT_WEB_APP_URL")
    key = os.environ.get("REPORT_ACCESS_KEY")
    env_file = REPO / ".deploy" / "vps.env"
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            if k == "REPORT_AUTH_INITIAL_ACCESS_KEY" and not key:
                key = v
    if not url:
        url = DEFAULT_URL
    if not key:
        sys.exit("Нет ключа: задайте REPORT_ACCESS_KEY или .deploy/vps.env")
    return url.rstrip("/"), key


def opener_with_cookies():
    jar = http.cookiejar.CookieJar()

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None  # 3xx после логина считаем успехом, не следуем

    return urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(jar), NoRedirect())


def request(op, base, path, data=None, headers=None, method=None, allow_3xx=False):
    req = urllib.request.Request(base + path, data=data, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with op.open(req, timeout=120) as resp:
            return resp.read()
    except urllib.error.HTTPError as e:
        if allow_3xx and 300 <= e.code < 400:
            return b""
        raise


def login(op, base, key):
    data = urllib.parse.urlencode({"accessKey": key}).encode()
    request(op, base, "/auth/access", data=data, allow_3xx=True,
            headers={"Content-Type": "application/x-www-form-urlencoded"})


def cmd_backup():
    base, key = load_env()
    op = opener_with_cookies()
    login(op, base, key)

    status = json.loads(request(op, base, "/api/backup"))
    if not status.get("enabled"):
        sys.exit("backup API отключён на сервере")

    resp = json.loads(request(op, base, "/api/backup", data=b"", method="POST"))
    backup = resp.get("backup") or {}
    name = backup.get("fileName") or (resp.get("backups") or [{}])[0].get("fileName")
    if not name:
        sys.exit(f"Не удалось получить имя архива: {resp}")

    out_dir = REPO / "backups"
    out_dir.mkdir(exist_ok=True)
    dest = out_dir / name
    dest.write_bytes(request(op, base, f"/api/backup/{name}/download"))

    with gzip.open(dest, "rt", encoding="utf-8") as f:
        archive = json.load(f)
    runs = len(archive.get("runs", []))
    messages = len(archive.get("messages", []))
    categories = len(archive.get("categories", []))
    if runs == 0:
        dest.unlink()
        sys.exit("Архив пуст (0 прогонов) — деплой не продолжать, разобраться")
    print(f"OK {dest} ({dest.stat().st_size} bytes): "
          f"runs={runs} messages={messages} categories={categories}")


def cmd_restore(file_name):
    base, key = load_env()
    path = REPO / "backups" / file_name
    if not path.exists():
        path = Path(file_name)
    if not path.exists():
        sys.exit(f"Файл не найден: {file_name}")

    op = opener_with_cookies()
    login(op, base, key)

    boundary = "----zcodebackup Boundary42"
    body = b"".join([
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
        f"filename=\"{path.name}\"\r\nContent-Type: application/gzip\r\n\r\n".encode(),
        path.read_bytes(), f"\r\n--{boundary}--\r\n".encode()
    ])
    resp = json.loads(request(op, base, "/api/backup/upload?restore=true", data=body,
                              headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}))
    print(json.dumps(resp, ensure_ascii=False))
    if not resp.get("ok"):
        sys.exit("Restore не удался")


def cmd_status():
    base, _ = load_env()
    try:
        build_id = urllib.request.urlopen(base + "/build-id.txt", timeout=30).read().decode()
        print("build-id:", " ".join(build_id.split()))
    except Exception as e:
        print("build-id недоступен:", e)
    base_url, key = load_env()
    op = opener_with_cookies()
    login(op, base, key)
    runs = json.loads(request(op, base, "/api/runs"))
    print(f"прогонов в «Все» (без Отладки): {len(runs)}")


if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] == "backup":
        cmd_backup()
    elif sys.argv[1] == "restore" and len(sys.argv) >= 3:
        cmd_restore(sys.argv[2])
    elif sys.argv[1] == "status":
        cmd_status()
    else:
        print(__doc__)
