package com.appscreener.report.service;

import com.appscreener.report.entity.TestRunEntity;
import com.appscreener.report.model.ReportType;
import com.appscreener.report.model.allure.AllureTestPageView;
import com.appscreener.report.repository.AllureTestResultRepository;
import com.appscreener.report.repository.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import({AllureResultsStorageService.class, AllureResultsParser.class, CategoryService.class})
class AllureResultsStorageServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("report.allure-results.storage-dir", () -> tempDir.resolve("allure").toString());
    }

    @Autowired
    private AllureResultsStorageService storage;

    @Autowired
    private TestRunRepository testRunRepository;

    @Autowired
    private AllureTestResultRepository testResultRepository;

    @Test
    void importZipCreatesRunWithSummaryAndCompactStorage() throws Exception {
        AllureResultsStorageService.ImportOutcome outcome = storage.importZip(
                sampleZip(false), null, "2",
                new AllureResultsStorageService.RunMeta(
                        "https://stand.example.com", "Smoke/All.xml", "https://gitlab/jobs/42", null));

        TestRunEntity run = outcome.run();
        assertNotNull(run.getId());
        assertEquals("api", run.getCategoryCode());
        assertEquals(ReportType.TEST_RUN_SUMMARY, run.getReportType());
        assertEquals(3, run.getTotalTests());
        assertEquals(1, run.getPassedTests());
        assertEquals(1, run.getFailedTests());
        assertEquals(1, run.getSkippedTests());
        assertEquals("https://stand.example.com", run.getStandUrl());
        assertEquals("Smoke/All.xml", run.getCiSuites());
        assertEquals("https://gitlab/jobs/42", run.getPipelineUrl());
        assertNotNull(run.getExecutionTime());
        assertTrue(run.getSuiteNamesJson().contains("Сюита примеров"));

        var results = testResultRepository.findByRunIdOrderBySortIndexAsc(run.getId());
        assertEquals(3, results.size());
        // полный JSON каждого теста хранится сжатым в БД
        results.forEach(r -> {
            assertNotNull(r.getResultJsonGz());
            assertTrue(r.getResultJsonGz().length > 0);
        });

        // аттачи — одним pak-файлом; loose-каталогов нет
        Path pack = storage.attachmentPackPath(run.getId());
        assertTrue(Files.exists(pack));
        assertEquals(3, packEntries(pack).size());
        assertFalse(Files.exists(storage.runDirectory(run.getId())));
    }

    @Test
    void importZipWithRootFolderInsideArchiveIsAccepted() throws Exception {
        AllureResultsStorageService.ImportOutcome outcome = storage.importZip(
                sampleZip(true), null, null, AllureResultsStorageService.RunMeta.empty());

        // категория без threadId → general
        assertEquals("general", outcome.run().getCategoryCode());
        assertEquals(3, outcome.parsed().results().size());
    }

    @Test
    void reimportSameRunReplacesPreviousData() throws Exception {
        AllureResultsStorageService.ImportOutcome first = storage.importZip(
                sampleZip(false), null, "4", AllureResultsStorageService.RunMeta.empty());
        String runId = first.run().getId();
        assertEquals(3, testResultRepository.findByRunIdOrderBySortIndexAsc(runId).size());

        storage.importZip(sampleZip(false), runId, "4", AllureResultsStorageService.RunMeta.empty());
        assertEquals(3, testResultRepository.findByRunIdOrderBySortIndexAsc(runId).size());
        assertEquals(1, testRunRepository.findAll().stream()
                .filter(r -> r.getId().equals(runId)).count());
    }

    @Test
    void importAttachesToExistingRunCreatedBySendMessage() throws Exception {
        TestRunEntity existing = new TestRunEntity();
        existing.setId("existingrun01");
        existing.setCategoryCode("ui");
        existing.setReceivedAt(java.time.Instant.now());
        existing.setReportType(ReportType.TEST_RUN_SUMMARY);
        existing.setTitle("Результаты тестирования appScreener");
        testRunRepository.save(existing);

        AllureResultsStorageService.ImportOutcome outcome = storage.importZip(
                sampleZip(false), "existingrun01", "4", AllureResultsStorageService.RunMeta.empty());

        assertEquals("existingrun01", outcome.run().getId());
        assertEquals(3, outcome.run().getTotalTests());
    }

    @Test
    void importRejectsZipWithoutResultFiles() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("not allure".getBytes());
        }
        try {
            storage.importZip(new ByteArrayInputStream(buf.toByteArray()), null, null,
                    AllureResultsStorageService.RunMeta.empty());
            throw new AssertionError("Ожидался IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("*-result.json"));
        }
    }

    @Test
    void testPageIsReproducibleFromDbAlone() throws Exception {
        AllureResultsStorageService.ImportOutcome outcome = storage.importZip(
                sampleZip(false), null, "2", AllureResultsStorageService.RunMeta.empty());
        String runId = outcome.run().getId();

        AllureTestPageView failed = storage.buildTestPage(runId, "22222222-2222-2222-2222-222222222222")
                .orElseThrow();
        assertEquals("failed", failed.getStatus());
        assertTrue(failed.getStatusTrace().contains("com.codeborne.selenide.ex.ElementShould"));
        assertEquals(1, failed.getSteps().size());
        assertEquals(1, failed.getStepRows().size());
        assertEquals(0, failed.getStepRows().get(0).depth());
        assertEquals(1, failed.getAttachments().size());
        assertTrue(failed.getAttachments().get(0).isImage());
        assertEquals("image/png", failed.getAttachments().get(0).getContentType());

        AllureTestPageView passed = storage.buildTestPage(runId, "11111111-1111-1111-1111-111111111111")
                .orElseThrow();
        // вложенный шаг: parent depth=0, child depth=1
        assertEquals(2, passed.getStepRows().size());
        assertEquals(1, passed.getStepRows().get(1).depth());
        assertEquals(1, passed.getAttachments().size());
        assertTrue(passed.getAttachments().get(0).isInlineText());
        assertTrue(passed.getAttachments().get(0).getInlineText().contains("SignalR-2.4.3"));

        // аннотации allure
        assertEquals("Sergej Kavalerov", passed.getOwner());
        assertEquals("critical", passed.getSeverity());
        assertEquals("severity-critical", passed.getSeverityClass());
        assertEquals("Модуль API", passed.getEpic());
        assertEquals("Проекты", passed.getFeature());
        assertEquals("Список проектов", passed.getStory());
        assertEquals(1, passed.getTags().size());
        assertEquals("smoke", passed.getTags().get(0));
        assertTrue(passed.getDescription().contains("GET /v1/projects"));
        assertEquals(1, passed.getLinks().size());
        assertEquals("issue", passed.getLinks().get(0).typeLabel());

        // шаги восстанавливаются из БД даже без пака аттачей на диске
        Files.delete(storage.attachmentPackPath(runId));
        AllureTestPageView failedFromDb = storage.buildTestPage(runId, "22222222-2222-2222-2222-222222222222")
                .orElseThrow();
        assertEquals(1, failedFromDb.getStepRows().size());
        assertTrue(failedFromDb.getStatusTrace().contains("com.codeborne.selenide.ex.ElementShould"));
        // метаданные аттача в БД, но содержимое уже не отдаётся
        assertEquals(1, failedFromDb.getAttachments().size());
        assertTrue(failedFromDb.getAttachments().get(0).getInlineText() == null
                || failedFromDb.getAttachments().get(0).getInlineText().isEmpty());

        assertTrue(storage.buildTestPage(runId, "missing").isEmpty());
        assertTrue(storage.buildTestPage("missrun", "11111111-1111-1111-1111-111111111111").isEmpty());
    }

    @Test
    void deleteRunDataRemovesEntitiesAndPack() throws Exception {
        AllureResultsStorageService.ImportOutcome outcome = storage.importZip(
                sampleZip(false), null, "2", AllureResultsStorageService.RunMeta.empty());
        String runId = outcome.run().getId();
        Path pack = storage.attachmentPackPath(runId);
        assertTrue(Files.exists(pack));

        storage.deleteRunData(List.of(runId));

        assertTrue(testResultRepository.findByRunIdOrderBySortIndexAsc(runId).isEmpty());
        assertFalse(Files.exists(pack));
    }

    /** Zip фикстуры: flat или с корневой папкой allure-results/. */
    static ByteArrayInputStream sampleZip(boolean withRootFolder) throws IOException {
        Path dir;
        try {
            dir = Paths.get(AllureResultsStorageServiceTest.class
                    .getResource("/allure-results-sample").toURI());
        } catch (Exception e) {
            throw new IOException("Фикстура allure-results-sample не найдена", e);
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf);
             var files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (withRootFolder) {
                    name = "allure-results/" + name;
                }
                zip.putNextEntry(new ZipEntry(name));
                zip.write(Files.readAllBytes(file));
                zip.closeEntry();
            }
        }
        return new ByteArrayInputStream(buf.toByteArray());
    }

    @Test
    void attachmentStreamedFromPackIntact() throws Exception {
        AllureResultsStorageService.ImportOutcome outcome = storage.importZip(
                sampleZip(false), null, "2", AllureResultsStorageService.RunMeta.empty());

        var attachment = storage.findAttachment(
                "bbbbbbbb-0000-0000-0000-000000000000-attachment.png").orElseThrow();
        // имя и тип — из JSON-рефа, а не из имени файла
        assertEquals("Тег не найден в модалке: pw403324u", attachment.getName());
        assertEquals("image/png", attachment.getContentType());

        Path original = Paths.get(AllureResultsStorageServiceTest.class
                .getResource("/allure-results-sample/bbbbbbbb-0000-0000-0000-000000000000-attachment.png").toURI());
        var handle = storage.openAttachmentStream(attachment).orElseThrow();
        ByteArrayOutputStream read = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        try (handle) {
            assertEquals(Files.size(original), handle.size());
            while ((n = handle.stream().read(chunk)) != -1) {
                read.write(chunk, 0, n);
            }
        }
        assertArrayEquals(Files.readAllBytes(original), read.toByteArray());

        var md = storage.findAttachment(
                "aaaaaaaa-0000-0000-0000-000000000000-attachment.md").orElseThrow();
        assertEquals("Метрики прогона", md.getName());
        assertEquals("text/markdown", md.getContentType());

        assertTrue(storage.findAttachment("no-such-attachment").isEmpty());
    }

    @Test
    void missingPackYieldsEmptyStream() throws Exception {
        AllureResultsStorageService.ImportOutcome outcome = storage.importZip(
                sampleZip(false), null, "2", AllureResultsStorageService.RunMeta.empty());
        String runId = outcome.run().getId();
        var attachment = storage.findAttachment(
                "bbbbbbbb-0000-0000-0000-000000000000-attachment.png").orElseThrow();

        Files.delete(storage.attachmentPackPath(runId));
        assertTrue(storage.openAttachmentStream(attachment).isEmpty());
    }

    @Test
    void formatDurationMatchesSummaryStyle() {
        assertEquals("00:00:00", AllureResultsStorageService.formatDuration(0));
        assertEquals("00:01:40", AllureResultsStorageService.formatDuration(100_000));
        assertEquals("02:05:09", AllureResultsStorageService.formatDuration(7_509_000));
    }

    @Test
    void importToleratesResultWithHugeStringValue() throws Exception {
        // реальный кейс: параметр шага содержит >20 млн символов (тело ответа) —
        // дефолтные StreamReadConstraints Jackson на этом падают
        Path dir = java.nio.file.Files.createTempDirectory("allure-huge");
        try {
            StringBuilder huge = new StringBuilder(21_000_100);
            java.util.Random random = new java.util.Random(1);
            while (huge.length() < 21_000_000) {
                huge.append(Long.toBinaryString(random.nextLong()));
            }
            String json = "{\"uuid\":\"huge00000-0000-0000-0000-000000000001\","
                    + "\"fullName\":\"tests.big.HugeParamTest.run\",\"name\":\"Тест с гигантским параметром\","
                    + "\"status\":\"passed\",\"statusDetails\":{},\"stage\":\"finished\","
                    + "\"steps\":[{\"name\":\"шаг\",\"status\":\"passed\",\"stage\":\"finished\",\"steps\":[],"
                    + "\"attachments\":[],\"parameters\":[{\"name\":\"response\",\"value\":\"" + huge + "\"}],"
                    + "\"start\":1792481587000,\"stop\":1792481588000}],"
                    + "\"attachments\":[],\"parameters\":[],\"start\":1792481587000,\"stop\":1792481589000}";
            java.nio.file.Files.writeString(dir.resolve("huge00000-0000-0000-0000-000000000001-result.json"), json);

            AllureResultsStorageService.ImportOutcome outcome = storage.importZip(
                    zipOfDir(dir), null, "2", AllureResultsStorageService.RunMeta.empty());

            assertEquals(1, outcome.parsed().results().size());
            var page = storage.buildTestPage(outcome.run().getId(),
                    "huge00000-0000-0000-0000-000000000001").orElseThrow();
            assertEquals(1, page.getStepRows().size());
            assertTrue(page.getStepRows().get(0).step().parameters().get(0).value().length() >= 21_000_000,
                    "гигантский параметр должен пройти насквозь");
        } finally {
            deleteRecursive(dir);
        }
    }

    private static ByteArrayInputStream zipOfDir(Path dir) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf);
             var files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
                java.nio.file.Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        return new ByteArrayInputStream(buf.toByteArray());
    }

    private static void deleteRecursive(Path path) throws IOException {
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void zipWithSuspiciousPathIsRejected() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            zip.putNextEntry(new ZipEntry("../../evil.txt"));
            zip.write("evil".getBytes());
        }
        try {
            storage.importZip(new ByteArrayInputStream(buf.toByteArray()), null, null,
                    AllureResultsStorageService.RunMeta.empty());
            throw new AssertionError("Ожидался IllegalArgumentException");
        } catch (IllegalArgumentException | IOException e) {
            // zip-slip отсечён: либо IllegalArgumentException от safeTarget, либо пустой каталог → нет result-файлов
        }
    }

    private static List<String> packEntries(Path pack) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(pack))) {
            List<String> names = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
            return names;
        }
    }
}
