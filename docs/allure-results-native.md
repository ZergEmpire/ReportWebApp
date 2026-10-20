# Нативный импорт allure-results в Report Web App (без Allure TestOps)

Status: `feature/allure-results-native` (ReportWebApp). Сервис сам читает JSON-формат
Allure (`*-result.json`, `*-container.json`, `*-attachment.*`) — как это делает Allure Report.
Allure TestOps для доставки данных больше не нужен.

## Зачем

Allure TestOps периодически нестабилен, а данные прогона и так полностью лежат в
`app/allure-results` после каждого запуска (их генерирует allure-testng адаптер).
Теперь TelegramTestListener заливает этот каталог в Report Web App, а Report Web App:

- считает сводку прогона (passed / failed+broken / skipped, длительность, сюиты);
- строит списки тестов с ссылками на страницу каждого теста;
- на странице теста показывает: дерево шагов с параметрами, message/stacktrace падения,
  скриншоты (png), текстовые/markdown/json/html/video аттачи (markdown-таблицы
  рендерятся в HTML, как в старом TestOps-флоу);
- **аннотации allure**: `@Owner` (👤 в шапке), `@Severity` (цветной бейдж),
  `@Epic › @Feature › @Story` (хлебная крошка раздела), `@Tags` (чипы),
  `@Description` (блок описания), `@Link`/`@Issue`/`@TmsLink` (ссылки с типом),
  параметры шагов (`@Step` с аргументами) — чипами под шагом;
- аттачи хранятся на сервере (`data/allure/{runId}-attachments.zip`) и раздаются
  стримингом через `/api/allure/attachments/{source}`.

## Контракт: POST /allure-results

`POST {REPORT_WEB_APP_URL}/allure-results` — multipart/form-data, авторизация не нужна
(как у `/sendMessage`).

| Поле | Тип | Обяз. | Описание |
|---|---|---|---|
| `archive` | file | да | zip каталога allure-results. Внутри — содержимое каталога или сам каталог `allure-results/` в корне архива |
| `message_thread_id` | text | нет | Категория как в TelegramBot (`2`=api, `4`=ui, `2154`=express, `6666`→general*, …). Нет — general |
| `run_id` | text | нет | Прикрепить к существующему прогону (например, к сводке, отправленной раньше через /sendMessage) |
| `stand_url` | text | нет | Стенд (STAND_URL) |
| `ci_suites` | text | нет | Значение CI_SUITES |
| `pipeline_url` | text | нет | Ссылка на pipeline job |
| `title` | text | нет | Заголовок прогона |

\* `6666`/`7777` отсутствуют в enum категорий Report Web App и попадают в «General»,
пока админ не заведёт кастомную категорию с таким threadId (или её не добавят в enum).

Ответ — в стиле Telegram Bot API:

```json
{
  "ok": true,
  "result": {
    "message_id": "98400383ce4d",
    "run_id": "98400383ce4d",
    "report_url": "https://…/report/98400383ce4d",
    "summary": {
      "total": 3, "passed": 0, "failed": 1, "broken": 2, "skipped": 0,
      "duration": "00:03:39",
      "suites": ["OSA ZIP+SBOM traces", "…"]
    },
    "tests": [
      {
        "uuid": "057e1732-…",
        "name": "Автотест загружает ZIP + SBOM …",
        "full_name": "tests.home_page.OSA.OsaAnalyse.…",
        "status": "broken",
        "url": "https://…/report/98400383ce4d/allure/057e1732-…"
      }
    ]
  }
}
```

Семантика счётчиков совпадает со сводкой TelegramListener:
`failed + broken` = «Проваленных (ошибка)», `skipped + unknown` = «Не запущенных».
Повторная загрузка с тем же `run_id` заменяет данные прогона (идемпотентно).

Лимиты: zip до 64 MB (multipart-лимит сервиса; распакованный каталог ~300 MB сжимается
в ~25 MB), до 50 000 файлов в архиве.

## Что поменять в TelegramTestListener (автотесты)

Порядок в `onFinish` (CI-ветка) вместо блока с `ApiAllureMethods`:

1. Зазиповать `app/allure-results` (каталог текущего прогона).
2. `POST {REPORT_WEB_APP_URL}/allure-results` с `archive`, `message_thread_id` (= THEMES_ID),
   `stand_url` (STAND_URL), `ci_suites` (CI_SUITES), `pipeline_url` (ссылка на job).
3. Из ответа собрать сводку: статистика — `summary`, имена и ссылки тестов — `tests[]`.
   Никаких обращений к TestOps API (`ALLURE_LAUNCH_ID`, access token) не нужно.
4. Отправить сводку в Telegram как раньше, но:
   - ссылки на тесты брать из `tests[].url` (страница теста в Report Web App),
     а не `https://dersecur.testops.cloud/launch/{id}/tree/{testResultId}`;
   - сразу после сводки отправить служебное сообщение со ссылкой
     `[📋 Отчёт в Report Web App](…/report/{run_id})` через `/sendMessage`
     **с `run_id` из ответа** — тогда текстовая сводка прикрепится к тому же прогону
     (дашборд покажет один прогон, а не два);
   - закрепить сообщение и обновить CSV-summary как раньше (messageId закреплённого
     сообщения Telegram, runId теперь от Report Web App).

Оба порядка работают: можно сначала `/sendMessage`-сводку (получить `message_id` = runId),
а затем залить zip с `run_id`; либо залить zip первым и слать текст с `run_id` из ответа.

### Утилита загрузки (пример для core)

Стиль проекта — статические утилиты в `core.methods.baseApiMethodsCore` на REST Assured:

```java
public final class AllureResultsUploader {

    private AllureResultsUploader() {
    }

    /** Заливает zip каталога allure-results; возвращает JSON-ответ result… как Map. */
    public static Map<String, Object> upload(Path resultsDir, String threadId) throws IOException {
        Path zip = zipResults(resultsDir);
        try {
            File zipFile = zip.toFile();
            RequestSpecification request = RestAssured.given()
                    .contentType(ContentType.MULTIPART)
                    .multiPart("archive", zipFile, "application/zip");
            if (threadId != null) {
                request.formParam("message_thread_id", threadId); // formParam с multipart ок
            }
            String standUrl = System.getenv("STAND_URL");
            if (standUrl != null) request.formParam("stand_url", standUrl);
            String ciSuites = System.getenv("CI_SUITES");
            if (ciSuites != null) request.formParam("ci_suites", ciSuites);
            String jobId = System.getenv("CI_JOB_ID");
            if (jobId != null) {
                request.formParam("pipeline_url",
                        "https://gitlab…/-/jobs/" + jobId); // подставить свой GitLab
            }
            Response response = request.when()
                    .post(ReportWebAppLinks.baseUrl() + "/allure-results");
            if (response.getStatusCode() != 200) {
                throw new IllegalStateException("allure-results upload failed: HTTP "
                        + response.getStatusCode() + " " + response.getBody().asString());
            }
            return response.jsonPath().getMap("result");
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    private static Path zipResults(Path resultsDir) throws IOException {
        Path zip = Files.createTempFile("allure-results-", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(
                Files.newOutputStream(zip, StandardOpenOption.TRUNCATE_EXISTING));
             Stream<Path> files = Files.list(resultsDir)) {
            for (Path file : files.sorted().toList()) {
                out.putNextEntry(new ZipEntry(file.getFileName().toString()));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        return zip;
    }
}
```

Вызов в listener-е:

```java
Map<String, Object> result = AllureResultsUploader.upload(
        Path.of("allure-results"), TelegramBot.getThemesId());
String runId = (String) result.get("run_id");
String reportUrl = (String) result.get("report_url");
@SuppressWarnings("unchecked")
Map<String, Object> summary = (Map<String, Object>) result.get("summary");
@SuppressWarnings("unchecked")
List<Map<String, Object>> tests = (List<Map<String, Object>>) result.get("tests");
```

Списки для батчей в Telegram: `tests` фильтруются по `status`
(`failed`,`broken` → «Упавшие», `passed` → «Успешные», иначе → «Не запущенные»),
ссылка элемента — `url`.

## Локальные прогоны

Для локального запуска (`CI_SUITES` не задан) — тот же upload с
`message_thread_id=local` (категория «Отладка»), либо оставить как есть
(локальные сводки и так уходят через `/sendMessage`).

## Проверка руками (curl)

```bash
cd app && python -m zipfile -c /tmp/allure-results.zip allure-results   # или zip -r
curl -s -X POST "https://<host>/allure-results" \
  -F "archive=@/tmp/allure-results.zip;type=application/zip" \
  -F "message_thread_id=4" \
  -F "stand_url=https://stand.example.com" \
  -F "ci_suites=UI/Smoke.xml"
```

## Хранение и память

Импорт спроектирован так, чтобы не переполнять heap сервера:

- загруженный архив **распаковывается во временный каталог на диске** (не в память);
  после импорта временный каталог удаляется;
- полный JSON результата каждого теста (шаги, параметры, аттач-рефы) хранится
  **сжатым (gzip) в БД** (`allure_test_results.result_json_gz`) — страница теста
  воспроизводится только из БД и попадает в бэкапы БД вместе с ней;
- аттачи (скриншоты/тексты/видео) складываются в **один deflate-пак на прогон**
  `data/allure/{runId}-attachments.zip` и отдаются **стримингом** — файл любого
  размера не грузится в память целиком (проверено: отдача 30 МБ видео занимает
  <1 МБ heap);
- текстовые аттачи на странице теста инлайнятся с лимитом 256 КБ;
- патологический `*-result.json` больше 32 МБ сохраняется урезанным (без шагов).

Retention удаляет пак вместе с прогоном. Импорт идемпотентен: повторная загрузка
с тем же `run_id` пересоздаёт записи и пак.

Бэкапы истории (`backups/history-*.json.gz`) **не включают аттачи** — по решению
от 20.10.2026 бэкап пока без паков: JSON результатов уже в БД (восстанавливается
вместе с ней), аттачи при потере диска пропадут у старых прогонов — приемлемо.
Если понадобится — копирование паков легко добавить в `BackupService`.

## Что остаётся от TestOps

Кнопки «Stacktrace из Allure» на старых прогонах продолжают работать через
TestOps API (если задан `ALLURE_TESTOPS_TOKEN`). Новые прогоны в TestOps не нуждаются:
у каждого теста своя страница со шагами, трейсом и аттачами.

## Поведение и нюансы

- Статус `unknown` считается в «Не запущенные» (skipped).
- Скриншоты и аттачи берутся из JSON-рефов (`{name, source, type}`); тип из JSON
  приоритетнее расширения файла (бывают `image/png` без расширения).
- Аттач, на который ссылаются и тест, и шаги, хранится один раз (в паке одна запись).
- Retest/rerun в одном каталоге = несколько result-записей; в сводку попадают все
  (как в Allure TestOps Launch с ретраями).
- Retention: allure-данные удаляются вместе с прогоном (по умолчанию 365 дней);
  JSON результатов — в БД (чистится транзакционно), аттачи — pak-файл на диске.
