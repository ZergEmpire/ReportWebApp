---
name: deploy-reportwebapp-timeweb
description: Деплой Report Web App на прод (Timeweb VPS) — полный ранбук с бэкапом до и восстановлением после. Использовать при любых просьбах «задеплой», «выкати на прод», «обнови сервер», «обнови Report Web App», «накатить версию», пуш/мерж для релиза, а также при проблемах после деплоя (пустой дашборд, старая версия, потеря данных).
---

# Деплой Report Web App на Timeweb VPS

Прод: https://zergempire-reportwebapp-b028.twc1.net (VPS 31.130.150.145).
Это **облачный сервер (VPS), НЕ App Platform** — авто-деплоя по пушу НЕТ,
деплой делается вручную через веб-консоль. Диск контейнера **эфемерный**:
при каждом редеплое БД стирается, поэтому порядок «бэкап ДО → деплой →
восстановление ПОСЛЕ» обязателен.

## Ранбук (порядок строгий)

### 1. Сборка и пуш

```bash
git checkout main && git fetch origin && git pull --ff-only origin main
git merge <feature-branch> && git push origin main
```

- Пуш отклонён (`non-fast-forward`) → **это нормально**: CI-бот коммитит пины
  в main (`Pin GHCR image to …`). Лечится `git fetch && git rebase origin/main`.
- Дальше CI (GitHub Actions «Docker image») собирает образ 5–10 минут и сам
  коммитит пин в `docker-compose.yml`. Ждать появления пин-коммита поллингом:
  `git fetch -q origin && git log --oneline origin/main -1 | grep "Pin GHCR image to <sha>"`.
- `gh` CLI и `jq` на этой машине НЕТ — JSON парсить python-ом, GHCR проверять
  анонимным токеном (`curl -s "https://ghcr.io/token?scope=repository:zergempire/report-web-app:pull"`).

### 2. Бэкап прода ДО деплоя (обязательно!)

```bash
python .agents/skills/deploy-reportwebapp-timeweb/scripts/timeweb_prod.py backup
```

Ключ доступа лежит в `.deploy/vps.env` (gitignored — в skill секреты не писать).
Скрипт логинится через `/auth/access`, делает `POST /api/backup`, скачивает
`backups/history-*.json.gz` и валидирует распаковкой (runs/messages/categories).
Без валидного бэкапа дальше не идти.

### 3. Деплой через веб-консоль Timeweb

1. Открыть https://timeweb.cloud/my/servers/8489067/console в Playwright-браузере.
   Если перекинуло на `/my/login` — **попросить пользователя самому ввести пароль
   в открывшемся окне** (логин `dk937840` подставляется сам). Пароль не спрашивать в чате.
2. Найти терминал: iframe с `textbox "Terminal input"` (после перезагрузки страницы
   ref меняется — искать заново через `browser_find`).
3. Вводить команды `browser_type` c `submit: true`. Вывод читать только скриншотом
   (`browser_take_screenshot`) + анализом изображения — в accessibility-дереве
   текста терминала нет.

Команды на сервере (проект в `/opt/report-web-app`, это НЕ git-репозиторий):

```bash
# страховочная копия данных контейнера на диск VPS
docker cp report-web-app-report-web-app-1:/app/data /root/safety-data-$(date +%F)
cd /opt/report-web-app
# переключить пин образа на sha нового коммита
sed -i 's|image: ghcr.io/zergempire/report-web-app:.*|image: ghcr.io/zergempire/report-web-app:<SHA>|' docker-compose.yml
docker compose pull 2>&1 | tail -2 && docker compose up -d 2>&1 | tail -3
```

### 4. Дождаться новой версии

Поллить с cache-buster (Caddy кэширует статику):

```bash
curl -s "https://zergempire-reportwebapp-b028.twc1.net/build-id.txt?n=$RANDOM"
```

Пока `build=` не равен новому SHA — версия не переехала (перекат ~1–2 минуты;
во время переката прод коротко отвечает 5xx/пусто — это норма).

### 5. Восстановление ПОСЛЕ

```bash
python .agents/skills/deploy-reportwebapp-timeweb/scripts/timeweb_prod.py restore backups/history-<...>.json.gz
python .agents/skills/deploy-reportwebapp-timeweb/scripts/timeweb_prod.py status
```

Рестор возвращает прогоны, сообщения и **кастомные категории** (qualitysast 🧬 6666,
qualityosa 🛡️ 7777, distr, notificationhealthservices — они живут в history-архиве).

### 6. Верификация

- `/api/runs` — количество прогонов сопоставимо с бэкапом («Все» = всего минус «Отладка»).
- `build-id.txt` = новый SHA.
- Страница прогона отвечает 200.

## Грабли этой сессии (не повторять)

| Затык | Причина / решение |
|---|---|
| Ждали авто-деплой 20+ минут впустую | VPS не следит за репо. Единственный путь — консоль вручную |
| `Permission denied (publickey)` по SSH | Ключи с этой машины на VPS не заведены. Веб-консоль — единственный вход |
| Push в main отклонён | Пин-коммит бота. `git fetch && git rebase origin/main` |
| `mvn package` падает — jar locked | Локальный тест-сервер держит jar (Windows). Сначала `scripts\stop-test-server.cmd`, потом сборка |
| Скрипты `scripts/*backup*.sh` падают | Требуют `jq`, его нет. Использовать `timeweb_prod.py` из этого skill |
| `strings` по старой `reportdb.mv.db` пуст | H2 сжимает данные. Источник правды по категориям — сам history-архив |
| Пустой дашборд у пользователя | Страница открыта до рестора / кэш. Ctrl+F5 |
| Нулевые счётчики в «Health monitor» | Норма: категория только из NOTIFICATION, статистика считает сводные прогоны |
| Терминал в консоли «пропал» | Рефрейм iframe после навигации. Пере-найти `Terminal input` |
| Дубликаты категорий при ручном создании | Рестор уже создаёт кастомные категории — руками не дублировать |

## Что НЕ переживает редеплой

- БД прогонов — восстанавливается из history-бэкапа (шаги 2 и 5).
- **Паки allure-аттачей** (`data/allure/*-attachments.zip`) — в бэкап НЕ входят
  (решение от 20.10.2026): у старых allure-прогонов пропадут скриншоты/аттачи,
  JSON результатов в БД сохранится. Изменить — добавить паки в `BackupService`.
- Страховочные копии на VPS (`/root/safety-data-*`) чистить руками раз в пару недель.
