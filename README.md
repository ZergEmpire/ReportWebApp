# Report Web App

Веб-приложение для приёма отчётов автотестов appScreener вместо Telegram.  
Данные хранятся в **файловой БД H2** (каталог `data/`), при первом запуске схема создаётся автоматически.

## Требования

| Компонент | Версия |
|-----------|--------|
| **JDK** | 21 (Spring Boot 3.x не запустится на Java 8/11) |
| **Maven** | 3.8+ |
| **Порт** | 8080 свободен |

Проверка Java:

```bash
java -version
# openjdk version "21" ...
```

На Windows, если в `PATH` остаётся Java 8, укажите **полный путь** к `java.exe` 21-й версии или выставьте `JAVA_HOME` на JDK 21 **до** вызова `java` / `mvn`.

## Быстрый старт (Docker)

Требуется **Docker** и **Docker Compose** v2.

**Локально** (с постоянными томами для H2):

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

Приложение: http://localhost:8080

**С демо-отчётами**:

```bash
REPORT_SEED_DEMO=true docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

PowerShell:

```powershell
$env:REPORT_SEED_DEMO = "true"
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

**Токен Allure TestOps** (опционально):

```bash
ALLURE_TESTOPS_TOKEN=ваш-токен docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

## Быстрый старт (локально)

```bash
mvn package -DskipTests
java -jar target/report-web-app-1.0-SNAPSHOT.jar
```

С демо-данными:

```bash
java -Dreport.seed-demo=true -jar target/report-web-app-1.0-SNAPSHOT.jar
```

| Страница | URL |
|----------|-----|
| Dashboard | http://localhost:8080 |
| Архив истории | http://localhost:8080/backup |
| Ключ доступа (админ) | http://localhost:8080/auth/admin/key |
| Категории (админ) | http://localhost:8080/auth/admin/categories |
| H2 Console | http://localhost:8080/h2-console |

---

## Интеграция автотестов с Web App

### 1. URL сервиса

В CI/CD или локально задайте базовый URL (без завершающего `/`):

```yaml
variables:
  REPORT_WEB_APP_URL: "https://reports.example.com"
```

В модуле `core` класс `TelegramBot` отправляет отчёты на:

```text
{REPORT_WEB_APP_URL}/sendMessage
```

Эндпоинты `/sendMessage` и `/pinChatMessage` **не требуют** сессии (исключены из авторизации). **Просмотр Dashboard и `/api/*` всегда требуют входа** — по ключу доступа или учётке администратора.

### 2. Как устроен один прогон в Dashboard

**На экране** вы видите одну карточку прогона: заголовок, стенд, счётчики passed/failed, а внутри — списки упавших/успешных/пропущенных тестов.

**По сети** эта карточка собирается из **нескольких HTTP-запросов** — так же, как раньше бот слал несколько сообщений в Telegram (сводка отдельно, списки отдельно). Web App **склеивает** их в один прогон, если вы передаёте общий идентификатор.

#### Зачем параметр `run_id`

| Без `run_id` | С `run_id` |
|--------------|------------|
| Каждый `sendMessage` может попасть в **разные** прогоны или «потеряться» | Все сообщения с одним `run_id` попадают **в одну** карточку на `/report/{id}` |
| Подходит только для одной сводки без списков | Нужно для полного отчёта (сводка + списки) |

**`run_id`** — это не «номер теста», а **номер прогона в Web App**. Его выдаёт сервер после первого сообщения (сводки). Во всех следующих запросах за этот же CI-job подставляете тот же `run_id`.

Имя `message_id` в ответе — наследие Telegram Bot API: для **сводки** в поле `result.message_id` лежит как раз этот `run_id`.

#### Пошагово (полный прогон)

Все шаги — обычный `POST /sendMessage`. Категория задаётся один раз через `message_thread_id` (например `2` = API).

| Шаг | Что отправляете | `run_id` в запросе | Что вернёт сервер | Зачем |
|-----|-----------------|-------------------|-------------------|-------|
| **1** | Текст **сводки** (стенд, статистика, ссылки) | **не передаёте** | `"message_id": "a1b2c3d4e5f6"` | Создаётся прогон; это его ID |
| **2** | Текст **упавших** тестов (секция «Упавшие тесты…») | `a1b2c3d4e5f6` | другой `message_id` (можно не сохранять) | Дополняет тот же прогон |
| **3** | Текст **успешных** тестов | `a1b2c3d4e5f6` | то же | то же |
| **4** | Текст **пропущенных** тестов | `a1b2c3d4e5f6` | то же | то же |
| **5** *(опционально)* | `POST /pinChatMessage?message_id=a1b2c3d4e5f6` | — | — | Карточка 📌 на Dashboard |

**Шаг 1 обязателен** для карточки на главной: без сводки с маркером «Результаты тестирования…» прогон в списке не появится (см. раздел про формат текста).

**Шаги 2–4** — по необходимости: если упавших нет, запрос с упавшими не шлёте. Порядок списков не важен.

**Минимальная интеграция:** только шаг 1 (одна сводка) — на Dashboard будет карточка без детальных списков тестов.

#### Краткий пример

```text
CI закончил API-тесты
  │
  ├─► sendMessage(сводка, thread=2)          → ответ: run_id = "a1b2c3d4e5f6"
  │
  ├─► sendMessage(3 упавших, thread=2, run_id=a1b2c3d4e5f6)
  ├─► sendMessage(20 успешных, thread=2, run_id=a1b2c3d4e5f6)
  │
  └─► pinChatMessage(message_id=a1b2c3d4e5f6)   ← по желанию

В браузере: одна карточка «API», внутри — сводка + 3 failed + 20 passed
```

#### Если уже работает TelegramBot в `core`

Менять логику не нужно: бот уже шлёт сводку, запоминает `message_id` ответа и подставляет его как `run_id` в следующие сообщения. Достаточно выставить `REPORT_WEB_APP_URL` вместо отправки в Telegram.

#### Если `run_id` забыли

Сервер попытается привязать список к **последнему** прогону той же категории за **30 минут**. Это запасной вариант; при параллельных pipeline’ах одной категории возможна путаница — **всегда передавайте `run_id` из шага 1**.

#### Уведомления без Allure (один запрос)

Для статусов стендов, алертов мониторинга и любой произвольной информации — **один** `sendMessage`, **без** `run_id` и **без** Allure.

Первая непустая строка текста должна начинаться с **`📣`** — по этому маркеру сервер понимает, что это уведомление, а не отчёт автотестов.

##### Как указать категорию (вкладку на Dashboard)

**Категория не пишется в тексте сообщения** — ни в заголовке после `📣`, ни в полях `*Метка:*`.  
В тексте только смысл уведомления. **Вкладка** (API, Health monitor, своя категория из админки) задаётся **одним HTTP-параметром**, как у автотестов:

| Параметр запроса | Роль |
|------------------|------|
| `message_thread_id` | В какую **вкладку** попадёт карточка |
| `text` | Что **написано** в карточке (начинается с `📣`) |

Тот же механизм, что в Telegram: раньше топик выбирался `message_thread_id`, сейчас он же выбирает вкладку в Web App.

| Нужна вкладка | Значение `message_thread_id` |
|---------------|------------------------------|
| Health monitor | `2152` |
| API | `2` |
| UI | `4` |
| General | `1` или не передавать параметр |
| Своя (создана в админке → **Категории**) | тот ID, что указали при создании, например `9001` |

Полный список встроенных ID — в разделе [Категории](#категории) ниже.

**Пример: уведомление во вкладку Health monitor**

```bash
curl -X POST "$REPORT_WEB_APP_URL/sendMessage" \
  --data-urlencode "parse_mode=Markdown" \
  --data-urlencode "message_thread_id=2152" \
  --data-urlencode "text=📣 Стенд API: проверка
========================
*Стенд:*
\`https://api.example.com\`
*Статус:*
✅ Доступен"
```

PowerShell:

```powershell
$base = "http://localhost:8080"
$text = Get-Content seed-payloads/stand-status-notification.txt -Raw -Encoding UTF8

Invoke-RestMethod -Uri "$base/sendMessage" -Method Post -Body @{
    text = $text
    parse_mode = "Markdown"
    message_thread_id = "2152"   # ← вкладка Health monitor; для API было бы "2"
}
```

Смените только `message_thread_id` — то же тело уйдёт в другую вкладку.  
Для **новой** вкладки: админка → **Категории** → создать категорию с полем `message_thread_id` (например `9100`) → в скрипте мониторинга всегда `message_thread_id=9100`.

Шаблон текста (параметры URL те же, что у автотестов):

```markdown
📣 Краткий заголовок
========================
*Стенд:*
`https://api.example.com`
*Статус:*
❌ HTTP 503, сервис не отвечает
*Детали:*
Падение с 12:40 MSK, эскалация в дежурную смену.

[Мониторинг](https://grafana.example.com/d/api)
```

| Элемент | Назначение |
|---------|------------|
| `📣 …` | Заголовок карточки на Dashboard |
| `*Метка:*` + текст / `` `url` `` | Поле (метка «Стенд» → ссылка на стенд в карточке) |
| `✅` `❌` `⚠️` `ℹ️` в начале строки | Строка статуса (для бейджа «успешно» / «есть ошибки») |
| `[текст](url)` | Кнопка-ссылка внизу карточки |

Пример в репозитории: `seed-payloads/stand-status-notification.txt`.

Ответ `result.message_id` — ID карточки (можно закрепить через `pinChatMessage`). Дополнительные сообщения для такого уведомления **не нужны**.

### 3. API `sendMessage`

```http
GET|POST {REPORT_WEB_APP_URL}/sendMessage
```

| Параметр | Обязательный | Описание |
|----------|--------------|----------|
| `text` | да | Тело отчёта в Markdown (как в Telegram) |
| `parse_mode` | нет | По умолчанию `Markdown` |
| `message_thread_id` | нет | **Вкладка Dashboard** (ID топика Telegram). Одинаково для автотестов и уведомлений `📣`. Пусто → General. Таблица — в [Категории](#категории) |
| `run_id` | нет | ID прогона из **шага 1** (значение `result.message_id` после сводки). В сводке не указывать |
| `chat_id` | нет | Игнорируется (совместимость с Bot API) |

Ответ при успехе:

```json
{"ok": true, "result": {"message_id": "abc123def456"}}
```

- После **сводки** сохраните `result.message_id` → это `run_id` для шагов 2–4 и для `pinChatMessage`.
- После **списка** приходит другой `message_id` — для склейки прогона он не нужен, нужен только `run_id` в запросе.

**Кодировка:** для кириллицы и эмодзи используйте UTF-8. В PowerShell удобно POST с `application/x-www-form-urlencoded` (см. `seed-v2.ps1`).

Пример PowerShell (сводка + список):

```powershell
$base = "http://localhost:8080"
$body = @{
    text = Get-Content seed-payloads/api-summary.txt -Raw -Encoding UTF8
    parse_mode = "Markdown"
    message_thread_id = "2"
}
$r = Invoke-RestMethod -Uri "$base/sendMessage" -Method Post -Body $body
$runId = $r.result.message_id

$failed = @"
========================
*Упавшие тесты с ошибкой: 
*
========================
❌ [API] Sample failed test
"@
Invoke-RestMethod -Uri "$base/sendMessage" -Method Post -Body @{
    text = $failed
    parse_mode = "Markdown"
    message_thread_id = "2"
    run_id = $runId
}
```

Пример `curl` (сводка):

```bash
curl -G "$REPORT_WEB_APP_URL/sendMessage" \
  --data-urlencode "parse_mode=Markdown" \
  --data-urlencode "message_thread_id=2" \
  --data-urlencode "text@seed-payloads/api-summary.txt"
```

### 4. Формат текста (чтобы UI распознал сообщение)

Парсер ориентируется на **те же маркеры**, что и Telegram-бот.

#### Сводка прогона (`TEST_RUN_SUMMARY`)

Должна содержать одну из фраз:

- `Результаты тестирования приложения appScreener`
- `☑️ Результаты тестирования`
- `✅ Результаты тестирования`

Рекомендуемая структура (см. `seed-payloads/api-summary.txt`):

```markdown
☑️ Результаты тестирования приложения appScreener.
========================
*Стенд, где проходил автотест: \n*
`https://stand.example.com`
*Название набора: \n*
`ApiSuiteStart`
*Значение переменной CI_SUITES для запуска Pipeline:\n*
`Patch/ApiSuiteStart.xml`
========================
*Статистика:\n*
Всего тестов: 24
Успешных: 24
Проваленных (ошибка): 0
Не запущенных (системная ошибка): 0
Время прохождения всех тестов: 00:45:12

[🔗 Ссылка на Pipeline](https://gitlab.example.com/jobs/2001)
[📊 Ссылка на Allure TestOps](https://dersecur.testops.cloud/launch/2001)
```

#### Суточная сводка (`DAILY_SUMMARY`)

Маркер: `📝` и `SUMMARY` в тексте.

#### Списки тестов

| Тип | Маркер в тексте | Строки тестов |
|-----|-----------------|---------------|
| Упавшие | `Упавшие тесты с ошибкой` | `❌ название` |
| Успешные | `Успешно пройденные тесты` | `✅ название` |
| Пропущенные | `не были запущены из-за системной ошибки` | `➡️ название` |

Заголовок секции и разделитель (как в боте):

```markdown
========================
*Упавшие тесты с ошибкой: 
*
========================
❌ [Login] Wrong password
❌ [Projects] Filter by rating
```

Для Allure-ссылок в строках используйте формат  
`[имя](https://dersecur.testops.cloud/launch/{launchId}/tree/{testResultId})` — на странице прогона появится кнопка stacktrace (если задан `ALLURE_TESTOPS_TOKEN`).

#### Наборы (suites)

- Успешные: `Успешно пройденные наборы`
- С ошибками: `Наборы, пройденные с ошибками`

#### Без данных Summary

Текст с `🚫 Данные для` и `Summary`.

#### Уведомление (`NOTIFICATION`)

Первая строка — `📣` и заголовок. Поля `*Метка:*`, строки статуса с `✅`/`❌`/`⚠️`/`ℹ️`. Подробнее — в разделе «Уведомления без Allure» выше.

### 5. `pinChatMessage`

```http
GET|POST {REPORT_WEB_APP_URL}/pinChatMessage?message_id={run_id}
```

Закрепляет **прогон** (сводку) на Dashboard. `chat_id` и токен бота не нужны.

---

## Категории

Категория определяет **вкладку на Dashboard** и фильтр `?category={code}`.

**Для любого сообщения** (отчёт автотестов, уведомление `📣`, одна сводка или несколько частей) категория задаётся **только** query/body-параметром **`message_thread_id`** в `sendMessage`. В Markdown-тексте категорию указывать не нужно.

### Встроенные категории (`message_thread_id`)

Раздел **«Все»** (`category=all`) показывает все прогоны **кроме «Отладка»**. Локальные — только во вкладке **🛠️ Отладка**.

| message_thread_id | Код (`?category=`) | Название |
|-------------------|-------------------|----------|
| 2152 | `healthy` | Health monitor |
| 2156 | `summary` | Summary |
| 4 | `ui` | UI |
| 2 | `api` | API |
| 2154 | `express` | Express тесты |
| 1 / *(пусто)* | `general` | General |
| 2148 | `updates` | Обновление базы SCA/SCS |
| 2158 | `alt` | AI |
| 2913 | `release` | Release/Patch |
| `local` / `localStart` / `debug` | `local` | Отладка |

В автотестах передавайте тот же `message_thread_id`, что использовался в Telegram-топике группы AUTOTEST AS/DS.

### Локальная отладка

Прогоны без `CI_SUITES` или с тегом `@localStart` в тексте → **Отладка**:

```http
message_thread_id=local
```

### Пользовательские категории (из UI)

1. Войдите как **администратор** (логин/пароль на странице входа).
2. Откройте **Категории**: http://localhost:8080/auth/admin/categories
3. Укажите:
   - **Название** — подпись вкладки;
   - **Код** — латиница (`security`, `perf-test`), для URL `?category=security`;
   - **message_thread_id** — уникальный ID для CI (например `9001`);
   - **Иконка** — из выпадающего списка.
4. В **любом** отправителе (CI автотестов, cron, скрипт мониторинга, curl) передавайте этот `message_thread_id` в каждом `sendMessage` — в том числе для уведомлений `📣` без Allure.

Новая категория сразу появляется в полосе вкладок Dashboard и в REST `GET /api/categories`.

**Пример:** создана категория «Статусы стендов» с `message_thread_id=9100`. Скрипт healthcheck раз в час:

```bash
curl -X POST "$REPORT_WEB_APP_URL/sendMessage" \
  -d "parse_mode=Markdown" \
  -d "message_thread_id=9100" \
  --data-urlencode "text=📣 Проверка стендов ..."
```

Список категорий для скриптов:

```bash
curl -s "$REPORT_WEB_APP_URL/api/categories" -H "Cookie: ..."   # при включённой авторизации — с сессией
```

Ответ — JSON-массив объектов `{ "threadId", "code", "label", "icon" }`.

---

## REST API (просмотр)

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/categories` | Вкладки Dashboard (встроенные + пользовательские) |
| GET | `/api/runs` | Список прогонов (`?category=api`, `local`, `all`, …) |
| GET | `/api/runs/{id}` | Детали прогона |
| GET | `/api/runs/{id}/messages` | Сырые сообщения прогона |
| GET | `/api/stats` | Сводная статистика (`?category=…`) |
| GET | `/api/backup` | Список архивов |
| POST | `/api/backup` | Сохранить историю |
| POST | `/api/backup/upload` | Загрузить `.json.gz` / `.zip` |
| GET | `/api/allure/testresult/{id}` | Stacktrace из Allure TestOps |
| GET | `/api/allure/status` | Настроен ли токен Allure |

При `report.auth.enabled=true` для `/api/*` нужна сессия после входа по ключу; `/sendMessage` остаётся открытым.

---

## Stacktrace из Allure TestOps

На странице прогона для упавших тестов со ссылкой  
`https://dersecur.testops.cloud/launch/{launchId}/tree/{testResultId}` — кнопка **«Запросить stacktrace»**.

Задайте токен:

```powershell
$env:ALLURE_TESTOPS_TOKEN = "ваш-api-token"
java -jar target/report-web-app-1.0-SNAPSHOT.jar
```

Или `application-local.properties` (см. `application-local.properties.example`).

Тестовый seed: `.\seed-allure-stacktrace-test.ps1`

---

## Демо-данные и seed-скрипты

| Скрипт | Назначение |
|--------|------------|
| `seed-v2.ps1` | Полные прогоны по категориям (UTF-8 POST) |
| `seed-demo-reports.ps1` | Альтернативный seed |
| `seed-allure-stacktrace-test.ps1` | Прогон для проверки Allure |

Примеры тел сообщений: каталог `seed-payloads/`.

---

## База данных

- **H2:** `./data/reportdb`
- **Таблица категорий:** `report_categories` (пользовательские)
- **Retention:** записи старше 365 дней удаляются по cron (`report.retention.days`)

Сброс: остановить app → удалить `data/` → запустить снова.

## Архив истории (Backup)

Снимок `history-*.json.gz` в `./backups`. UI: http://localhost:8080/backup  
Архив включает:

- прогоны (`test_runs`);
- сообщения прогонов (`report_messages`);
- пользовательские категории (`report_categories`).

При восстановлении архива пользовательские категории также восстанавливаются.

```bash
curl -F "file=@history-20260521-120000.json.gz" "http://localhost:8080/api/backup/upload?restore=true"
```

## Авторизация

**UI закрыт без входа.** Интерцептор перенаправляет на `/auth/login`, если нет сессии.

| Способ входа | Доступ |
|--------------|--------|
| **Ключ доступа** | Dashboard, отчёты, архив, `/api/*` (кроме `/sendMessage`) |
| **Администратор** | То же + **Ключ доступа** и **Категории** в шапке |

Ключ доступа выдаёт администратор (страница **Ключ доступа** → «Сгенерировать»). Обычный ключ **не** открывает админку.

**Чтобы попасть в админку:** `/auth/login` → блок **«Вход администратора»** (или ссылка **«Вход администратора»** в шапке, если вы вошли по ключу).

На Timeweb задайте в переменных окружения:

| Переменная | Назначение |
|------------|------------|
| `REPORT_AUTH_ADMIN_USERNAME` | Логин админа |
| `REPORT_AUTH_ADMIN_PASSWORD` | Пароль админа (**обязательно** смените на стенде) |
| `REPORT_AUTH_INITIAL_ACCESS_KEY` | Стартовый ключ доступа при развёртке (по умолчанию `report-web-app-access-key`) |

Переменная **`REPORT_AUTH_ENABLED` больше не используется** — авторизация UI всегда включена в коде.  
Если на стенде осталось `REPORT_AUTH_ENABLED=false` от старых настроек, **удалите её** из панели Timeweb (иначе в старых сборках UI открывался без входа).

Стартовый ключ читается из `REPORT_AUTH_INITIAL_ACCESS_KEY` на каждом запуске, поэтому после деплоя можно использовать один и тот же установочный ключ в документации и CI.

## Типичные проблемы

| Симптом | Решение |
|---------|---------|
| Пустой Dashboard, сообщения есть | Нет сводки с маркером «Результаты тестирования» — только списки |
| Уведомление не появилось | Первая строка должна начинаться с `📣` |
| Уведомление не в той вкладке | Меняется **`message_thread_id` в URL/body**, не текст сообщения |
| Списки не в том прогоне | Сохраните `message_id` после сводки и передайте его как `run_id` во все списки |
| Кракозябры в PowerShell | POST UTF-8, см. `seed-v2.ps1` |
| `UnsupportedClassVersionError` | JDK 21 |
| Новая категория не видна | Проверьте `message_thread_id` в CI и код вкладки в URL |

## MVP ограничения

- Только текст/Markdown (фото не поддерживаются)
- `/sendMessage` без авторизации — ограничьте сеть на production

## Сборка

```bash
mvn package -DskipTests
```

Артефакт: `target/report-web-app-1.0-SNAPSHOT.jar`
