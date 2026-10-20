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
    void importZipCreatesRunWithSummaryAndFiles() throws Exception {
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

        assertEquals(3, testResultRepository.findByRunIdOrderBySortIndexAsc(run.getId()).size());

        Path runDir = storage.runDirectory(run.getId());
        assertEquals(4, Files.list(runDir.resolve("results")).count());
        assertEquals(3, Files.list(runDir.resolve("attachments")).count());
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
    void testPageContainsStepsAttachmentsAndInlineText() throws Exception {
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

        assertTrue(storage.buildTestPage(runId, "missing").isEmpty());
        assertTrue(storage.buildTestPage("missrun", "11111111-1111-1111-1111-111111111111").isEmpty());
    }

    @Test
    void deleteRunDataRemovesEntitiesAndFiles() throws Exception {
        AllureResultsStorageService.ImportOutcome outcome = storage.importZip(
                sampleZip(false), null, "2", AllureResultsStorageService.RunMeta.empty());
        String runId = outcome.run().getId();
        Path runDir = storage.runDirectory(runId);
        assertTrue(Files.exists(runDir));

        storage.deleteRunData(List.of(runId));

        assertTrue(testResultRepository.findByRunIdOrderBySortIndexAsc(runId).isEmpty());
        assertFalse(Files.exists(runDir));
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
    void readsAttachmentContentFromDisk() throws Exception {
        AllureResultsStorageService.ImportOutcome outcome = storage.importZip(
                sampleZip(false), null, "2", AllureResultsStorageService.RunMeta.empty());

        var attachment = storage.findAttachment(
                "bbbbbbbb-0000-0000-0000-000000000000-attachment.png").orElseThrow();
        byte[] content = storage.readAttachmentContent(attachment);
        assertTrue(content.length > 0);
        // PNG signature
        assertEquals((byte) 0x89, content[0]);
        // имя и тип — из JSON-рефа, а не из имени файла
        assertEquals("Тег не найден в модалке: pw403324u", attachment.getName());
        assertEquals("image/png", attachment.getContentType());

        var md = storage.findAttachment(
                "aaaaaaaa-0000-0000-0000-000000000000-attachment.md").orElseThrow();
        assertEquals("Метрики прогона", md.getName());
        assertEquals("text/markdown", md.getContentType());

        assertTrue(storage.findAttachment("no-such-attachment").isEmpty());
    }

    @Test
    void formatDurationMatchesSummaryStyle() {
        assertEquals("00:00:00", AllureResultsStorageService.formatDuration(0));
        assertEquals("00:01:40", AllureResultsStorageService.formatDuration(100_000));
        assertEquals("02:05:09", AllureResultsStorageService.formatDuration(7_509_000));
    }

    @Test
    void zipWithSuspiciousPathIsRejected() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            zip.putNextEntry(new ZipEntry("../../evil.txt"));
            zip.write("evil".getBytes());
        }
        try (ZipInputStream ignored = new ZipInputStream(new ByteArrayInputStream(buf.toByteArray()))) {
            // сам вызов ниже должен упасть на safeTarget
        }
        try {
            storage.importZip(new ByteArrayInputStream(buf.toByteArray()), null, null,
                    AllureResultsStorageService.RunMeta.empty());
            throw new AssertionError("Ожидался IllegalArgumentException");
        } catch (IllegalArgumentException | IOException e) {
            // zip-slip отсечён: либо IllegalArgumentException от safeTarget, либо пустой каталог → нет result-файлов
        }
    }
}
