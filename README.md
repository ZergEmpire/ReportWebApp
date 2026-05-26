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

На Windows, если в `PATH` остаётся Java 8, укажите **полный путь** к `java.exe` 21-й версии (см. примеры ниже) или выставьте `JAVA_HOME` на JDK 21 **до** вызова `java` / `mvn`.

## Быстрый старт (Docker)

Требуется **Docker** и **Docker Compose** v2.

**Локально** (с постоянными томами для H2):

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

Приложение: http://localhost:8080

**С демо-отчётами** (первый запуск с примерами в Dashboard):

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

Данные H2 и архивы хранятся в именованных томах Docker (`report-data`, `report-backups`). Сброс БД:

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml down -v
```

## Деплой на Timeweb Cloud (App Platform, Docker Compose)

В корне репозитория лежит **`docker-compose.yml`** без `volumes` — это требование [документации Timeweb](https://timeweb.cloud/docs/apps/deploying-with-docker-compose) (иначе сборка падает).

| Параметр в панели | Значение |
|-------------------|----------|
| Тип | Docker → **Docker Compose** |
| Репозиторий | `ReportWebApp`, ветка `main` |
| Порт приложения | **8080** (первый сервис в compose проксируется на домен) |
| Регион | Россия |

Переменные в панели (по желанию): `ALLURE_TESTOPS_TOKEN`, `REPORT_SEED_DEMO=true`.

**Данные H2:** без томов в compose БД живёт внутри контейнера и **сбрасывается при пересборке**. Для постоянного диска уточните в поддержке Timeweb опцию persistent storage для App Platform или позже перейдите на PostgreSQL.

После `git push` нажмите **Перезапустить деплой** в панели.

## Быстрый старт (локально)

### 1. Сборка

Из корня модуля `report-web-app`:

```bash
cd report-web-app
mvn package -DskipTests
```

### 2. Запуск

**Обычный запуск** (пустая БД или продолжение с существующей в `./data/`):

```bash
java -jar target/report-web-app-1.0-SNAPSHOT.jar
```

**С демо-отчётами** (все категории + раздел «Отладка») — удобно для первого знакомства с UI:

```bash
java -Dreport.seed-demo=true -jar target/report-web-app-1.0-SNAPSHOT.jar
```

PowerShell (если `java` в PATH — старая версия):

```powershell
cd report-web-app
$env:JAVA_HOME = "C:\Users\Sergej\.jdks\temurin-21.0.11"
& "$env:JAVA_HOME\bin\java.exe" "-Dreport.seed-demo=true" -jar target/report-web-app-1.0-SNAPSHOT.jar
```

**Через Maven** (без сборки JAR):

```bash
mvn spring-boot:run
# с демо:
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dreport.seed-demo=true"
```

### 3. Открыть в браузере

| Страница | URL |
|----------|-----|
| Dashboard | http://localhost:8080 |
| Архив истории | http://localhost:8080/backup |
| H2 Console (отладка БД) | http://localhost:8080/h2-console |

Параметры подключения в H2 Console:

- JDBC URL: `jdbc:h2:file:./data/reportdb` (относительно каталога `report-web-app`, откуда запущен процесс)
- User: `sa`
- Password: *(пусто)*

## Что появляется на диске

При первом запуске создаются каталоги рядом с приложением:

```
report-web-app/
  data/              # файлы H2 (reportdb.mv.db, …) — основное хранилище
  backups/           # архивы истории history-*.json.gz (если включён backup)
  target/            # артефакт сборки
```

**Сброс всех отчётов:** остановите приложение, удалите каталог `data/`, запустите снова.

**Повторная заливка демо** поверх существующей БД добавит ещё прогоны; для чистого демо — удалите `data/` и запустите с `-Dreport.seed-demo=true`.

## Подключение автотестов

В переменных окружения или CI:

```yaml
variables:
  REPORT_WEB_APP_URL: "http://localhost:8080"
```

Класс `TelegramBot` в модуле `core` шлёт отчёты на `{REPORT_WEB_APP_URL}/sendMessage` (совместимый API).

Локальные прогоны без `CI_SUITES` попадают в раздел **🛠️ Отладка** (`message_thread_id=local`, в тексте тег `@localStart`).

## API (совместимость с Telegram Bot)

### Отправить отчёт

```
GET|POST {REPORT_WEB_APP_URL}/sendMessage?text={markdown}&parse_mode=Markdown&message_thread_id={optional}&run_id={optional}
```

Ответ:

```json
{"ok": true, "result": {"message_id": "abc123def456"}}
```

Для списков тестов после сводки передайте `run_id` = `message_id` сводного сообщения (как в `TelegramListener`).

### Закрепить отчёт

```
POST|GET {REPORT_WEB_APP_URL}/pinChatMessage?message_id={id}
```

Параметры `chat_id` и токен бота **не требуются**.

## REST API

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/runs` | Список прогонов (`?category=api`, `local`, `all`, …) |
| GET | `/api/runs/{id}` | Детали прогона |
| GET | `/api/runs/{id}/messages` | Сырые сообщения прогона |
| GET | `/api/stats` | Сводная статистика (`?category=…`) |
| GET | `/api/backup` | Список архивов истории |
| POST | `/api/backup` | Сохранить историю сейчас |
| POST | `/api/backup/upload` | Загрузить `.json.gz` или `.zip` (`file`, `restore=true` — сразу накатить) |
| GET | `/api/backup/{fileName}/download` | Скачать архив |
| POST | `/api/backup/restore/{fileName}` | Накатить архив из каталога на сервере |
| GET | `/api/allure/testresult/{id}` | Детали теста из Allure TestOps (message, stacktrace) |
| GET | `/api/allure/status` | Настроен ли токен Allure |

### Stacktrace из Allure TestOps

На странице прогона для **упавших** тестов со ссылкой `https://dersecur.testops.cloud/launch/{launchId}/tree/{testResultId}` появляется кнопка **«Запросить stacktrace»**.

**Цепочка API Allure TestOps:**

1. Список упавших в launch: `GET /api/launch/{launchId}/unresolved?size=10&page=0` — в `content[]` поле **`id`** (например `117352`) — это **test result id**.
2. Stacktrace: `GET /api/testresult/{id}` — подставляется **`id` из п.1**, не `testCaseId` (например `6778`).

Пример: [launch 59211](https://dersecur.testops.cloud/launch/59211) → test result `117352` → [API](https://dersecur.testops.cloud/api/testresult/117352).

Тестовый seed: `.\seed-allure-stacktrace-test.ps1` (нужен запущенный jar на `:8080`).

Перед запуском задайте API-токен Allure TestOps (профиль → API Tokens):

```powershell
# Вариант 1 (рекомендуется): файл в корне модуля, не попадает в git
Copy-Item application-local.properties.example application-local.properties
# отредактируйте report.allure.api-token в application-local.properties
java -jar target/report-web-app-1.0-SNAPSHOT.jar

# Вариант 2: переменная окружения
$env:ALLURE_TESTOPS_TOKEN = "ваш-api-token"
java -jar target/report-web-app-1.0-SNAPSHOT.jar
```

Токен **не** кладите в `src/main/resources/application.properties` — он уйдёт в репозиторий.

Для **локальных** прогонов без ссылок на TestOps кнопка не показывается — нужен CI-отчёт с `ALLURE_LAUNCH_ID` и ссылками на tree/{id}.

## Категории (как темы в Telegram)

Раздел **«Все»** на Dashboard показывает все прогоны **кроме «Отладка»** (`local`). Локальные прогоны видны только в **🛠️ Отладка**.

| message_thread_id | Категория |
|-------------------|-----------|
| 2152 | Health monitor |
| 2156 | Summary |
| 4 | UI |
| 2 | API |
| 2154 | Express тесты |
| 1 / нет | General |
| 2148 | Обновление базы SCA/SCS |
| 2158 | AI |
| 2913 | Release/Patch |
| `local` / `localStart` / `debug` | Отладка |

## База данных и хранение

- **Движок:** H2, файл `./data/reportdb`
- **Схема:** `spring.jpa.hibernate.ddl-auto=update` — таблицы создаются/обновляются при старте
- **Retention:** записи старше **365 дней** удаляются по cron (03:00), параметр `report.retention.days`

Настройки в `src/main/resources/application.properties`.

## Архив истории (Backup)

Компактный снимок прогонов и сообщений: `history-YYYYMMDD-HHmmss.json.gz` (не полный дамп диска H2).

| Параметр | По умолчанию | Описание |
|----------|--------------|----------|
| `report.backup.enabled` | `true` | Включить архивацию |
| `report.backup.directory` | `./backups` | Каталог архивов |
| `report.backup.cron` | `0 0 3 * * SUN` | Раз в неделю |
| `report.backup.keep-count` | `8` | Сколько архивов хранить |
| `report.backup.skip-if-unchanged` | `true` | Не создавать копию без изменений |
| `report.backup.on-startup` | `false` | Не архивировать при старте |

Плановый архив пропускается, если отчётов нет или история не менялась с прошлого раза.

**Загрузка на стенд:** http://localhost:8080/backup → блок «Загрузить архив с компьютера». Поддерживаются `.json.gz` и `.zip` (внутри — `history*.json.gz` или `history*.json`). Файл сохраняется в `backups/`; при включённой галочке сразу подменяет данные в H2.

Пример API:

```bash
curl -F "file=@history-20260521-120000.json.gz" "http://localhost:8080/api/backup/upload?restore=true"
```

## Типичные проблемы

| Симптом | Решение |
|---------|---------|
| `UnsupportedClassVersionError` … 52.0 | Запуск на Java 8; используйте JDK **21** |
| Порт 8080 занят | Остановите другой процесс или смените `server.port` |
| Пустой Dashboard без демо | Запустите с `-Dreport.seed-demo=true` или дождитесь отчётов от автотестов |
| Ошибка при `seed-demo` | Пересоберите `mvn package` после изменений в `seed-payloads/` |
| Битая БД | Остановить app → удалить `data/` → запустить снова |

## MVP ограничения

- Отправка фото не поддерживается (только текст/Markdown)
- Без авторизации (для production ограничьте доступ к `/backup` и API)

## Модуль в репозитории

Собирается из родительского `pom.xml` (`artifactId`: `report-web-app`).  
Для production позже можно вынести в отдельный репозиторий и заменить H2 на PostgreSQL без смены API автотестов.
