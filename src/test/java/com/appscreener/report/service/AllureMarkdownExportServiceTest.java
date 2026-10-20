package com.appscreener.report.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({AllureMarkdownExportService.class, AllureResultsStorageService.class,
        AllureResultsParser.class, CategoryService.class})
class AllureMarkdownExportServiceTest {

    private static final String BASE = "http://test.example.com";

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("report.allure-results.storage-dir", () -> tempDir.resolve("allure").toString());
    }

    @Autowired
    private AllureMarkdownExportService export;

    @Autowired
    private AllureResultsStorageService storage;

    private String importFixture() throws IOException {
        return storage.importZip(new java.io.ByteArrayInputStream(sampleZip()), null, "2",
                new AllureResultsStorageService.RunMeta(
                        "https://stand.example.com", "Smoke/All.xml", "https://gitlab/jobs/42", null))
                .run().getId();
    }

    @Test
    void runExportContainsSummarySectionsAndTestLinks() throws Exception {
        String runId = importFixture();
        String md = export.exportRun(runId, BASE);

        assertNotNull(md);
        assertTrue(md.startsWith("# Отчёт прогона:"));
        assertTrue(md.contains("## Сводка"));
        assertTrue(md.contains("| Всего тестов | 3 |"));
        assertTrue(md.contains("| Успешных | 1 |"));
        assertTrue(md.contains("| Проваленных (ошибка) | 1 |"));
        assertTrue(md.contains("https://stand.example.com"));
        assertTrue(md.contains("Smoke/All.xml"));
        assertTrue(md.contains("Сюита примеров"));

        // секция упавших: детально с сообщением и ссылкой
        assertTrue(md.contains("## ❌ Упавшие тесты (1)"));
        assertTrue(md.contains("Element should have text"));
        assertTrue(md.contains(BASE + "/report/" + runId + "/allure/22222222-2222-2222-2222-222222222222"));

        // компактные списки: по строке-ссылке на каждый тест
        assertEquals(3, countOccurrences(md, "](" + BASE + "/report/" + runId + "/allure/"));
        assertTrue(md.contains("## ✅ Успешно пройденные (1)"));
        assertTrue(md.contains("## ➡️ Не запущенные / прочие (1)"));

        assertNull(export.exportRun("no-such-run", BASE));
    }

    @Test
    void testExportContainsAnnotationsStepsParamsAndAttachments() throws Exception {
        String runId = importFixture();
        String md = export.exportTest(runId, "11111111-1111-1111-1111-111111111111", BASE);

        assertNotNull(md);
        assertTrue(md.startsWith("# Тест:"));
        assertTrue(md.contains("`passed`"));
        assertTrue(md.contains("Владелец"));  // аннотации
        assertTrue(md.contains("Модуль API › Проекты › Список проектов"));
        assertTrue(md.contains("GET /v1/projects"));  // description
        assertTrue(md.contains("gitlab.example.com"));  // links

        // шаги с параметрами и вложенностью
        assertTrue(md.contains("- passed: Базовый GET-запрос на endpoint: /v1/projects"));
        assertTrue(md.contains("параметры: `request`=`/v1/projects`"));
        assertTrue(md.contains("`statusCode`=`200`"));

        // аттач с содержимым и абсолютной ссылкой
        assertTrue(md.contains("## Вложения (attachments)"));
        assertTrue(md.contains(BASE + "/api/allure/attachments/aaaaaaaa-0000-0000-0000-000000000000-attachment.md"));
        assertTrue(md.contains("SignalR-2.4.3"));

        assertNull(export.exportTest(runId, "missing", BASE));
    }

    @Test
    void failedTestExportContainsMessageAndTraceBlocks() throws Exception {
        String runId = importFixture();
        String md = export.exportTest(runId, "22222222-2222-2222-2222-222222222222", BASE);

        assertTrue(md.contains("## Ошибка падения"));
        assertTrue(md.contains("Element should have text"));
        assertTrue(md.contains("com.codeborne.selenide.ex.ElementShould"));
        // аттач-скриншот: ссылка без содержимого (бинарный)
        assertTrue(md.contains(BASE + "/api/allure/attachments/bbbbbbbb-0000-0000-0000-000000000000-attachment.png"));
        assertFalse(md.contains("```text\n�"));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    static byte[] sampleZip() throws IOException {
        Path dir;
        try {
            dir = Paths.get(AllureMarkdownExportServiceTest.class
                    .getResource("/allure-results-sample").toURI());
        } catch (Exception e) {
            throw new IOException("Фикстура allure-results-sample не найдена", e);
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf);
             var files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
                zip.write(Files.readAllBytes(file));
                zip.closeEntry();
            }
        }
        return buf.toByteArray();
    }
}
