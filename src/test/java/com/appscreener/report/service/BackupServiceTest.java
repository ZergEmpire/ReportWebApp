package com.appscreener.report.service;

import com.appscreener.report.backup.ReportHistorySnapshot;
import com.appscreener.report.config.BackupProperties;
import com.appscreener.report.entity.ReportCategoryEntity;
import com.appscreener.report.entity.ReportMessageEntity;
import com.appscreener.report.entity.TestRunEntity;
import com.appscreener.report.model.ReportType;
import com.appscreener.report.repository.ReportCategoryRepository;
import com.appscreener.report.repository.ReportMessageRepository;
import com.appscreener.report.repository.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(BackupService.class)
@EnableConfigurationProperties(BackupProperties.class)
class BackupServiceTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("report.backup.directory", () -> tempDir.resolve("backups").toString());
        registry.add("report.backup.enabled", () -> "true");
        registry.add("report.backup.skip-if-unchanged", () -> "true");
        registry.add("report.backup.keep-count", () -> "3");
    }

    @Autowired
    private BackupService backupService;

    @Autowired
    private TestRunRepository testRunRepository;

    @Autowired
    private ReportMessageRepository messageRepository;

    @Autowired
    private ReportCategoryRepository categoryRepository;

    @Test
    void archiveRoundTrip() throws Exception {
        ReportCategoryEntity category = new ReportCategoryEntity();
        category.setCode("security");
        category.setThreadId("9001");
        category.setLabel("Security");
        category.setIcon("🔒");
        category.setSortOrder(101);
        categoryRepository.save(category);

        TestRunEntity run = new TestRunEntity();
        run.setId("run1");
        run.setCategoryCode("api");
        run.setReceivedAt(Instant.parse("2026-05-19T10:00:00Z"));
        run.setReportType(ReportType.TEST_RUN_SUMMARY);
        run.setTitle("API run");
        run.setTotalTests(10);
        run.setPassedTests(10);
        run.setFailedTests(0);
        testRunRepository.save(run);

        ReportMessageEntity msg = new ReportMessageEntity();
        msg.setId("msg1");
        msg.setTestRun(run);
        msg.setReceivedAt(run.getReceivedAt());
        msg.setReportType(ReportType.PASSED_TESTS);
        msg.setRawText("ok");
        messageRepository.save(msg);

        var created = backupService.createBackup(true);
        assertTrue(!created.isSkipped());
        assertTrue(created.getBackup().getFileName().endsWith(".json.gz"));
        assertEquals(1, created.getBackup().getRunCount());
        assertEquals(1, created.getBackup().getMessageCount());

        messageRepository.deleteAll();
        testRunRepository.deleteAll();
        categoryRepository.deleteAll();
        assertEquals(0, testRunRepository.count());

        backupService.restoreBackup(created.getBackup().getFileName());

        assertEquals(1, testRunRepository.count());
        assertEquals(1, messageRepository.count());
        assertEquals(1, categoryRepository.count());
        assertEquals("Security", categoryRepository.findByCodeIgnoreCase("security").orElseThrow().getLabel());
        assertEquals("API run", testRunRepository.findById("run1").orElseThrow().getTitle());
    }

    @Test
    void scheduledSkipsWhenUnchanged() throws Exception {
        TestRunEntity run = new TestRunEntity();
        run.setId("r2");
        run.setCategoryCode("ui");
        run.setReceivedAt(Instant.now());
        run.setReportType(ReportType.TEST_RUN_SUMMARY);
        run.setTitle("UI");
        testRunRepository.save(run);

        backupService.createBackup(true);

        var second = backupService.createBackup(false);
        assertTrue(second.isSkipped());
    }

    @Test
    void rejectsUnsafeFileName() {
        assertTrue(backupService.resolveBackupFile("../evil.json.gz").isEmpty());
        assertTrue(backupService.resolveBackupFile("reportdb-old.zip").isEmpty());
    }

    @Test
    void restoreEmptyArchiveDoesNotWipeDatabase() throws Exception {
        TestRunEntity run = new TestRunEntity();
        run.setId("keep1");
        run.setCategoryCode("api");
        run.setReceivedAt(Instant.now());
        run.setReportType(ReportType.TEST_RUN_SUMMARY);
        run.setTitle("Keep me");
        testRunRepository.save(run);

        Path dir = tempDir.resolve("backups");
        Files.createDirectories(dir);
        String name = "history-20260521-000000.json.gz";
        ReportHistorySnapshot empty = new ReportHistorySnapshot();
        empty.setExportedAt(Instant.now());
        try (var out = Files.newOutputStream(dir.resolve(name));
             var gzip = new java.util.zip.GZIPOutputStream(out)) {
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .writeValue(gzip, empty);
        }

        assertEquals(1, testRunRepository.count());
        try {
            backupService.restoreBackup(name);
            org.junit.jupiter.api.Assertions.fail("expected empty archive to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("пустой"));
        }
        assertEquals(1, testRunRepository.count());
    }

    @Test
    void uploadArchiveFromBytes() throws Exception {
        TestRunEntity run = new TestRunEntity();
        run.setId("up1");
        run.setCategoryCode("api");
        run.setReceivedAt(Instant.now());
        run.setReportType(ReportType.TEST_RUN_SUMMARY);
        run.setTitle("Uploaded run");
        testRunRepository.save(run);

        var created = backupService.createBackup(true);
        byte[] bytes = java.nio.file.Files.readAllBytes(
                tempDir.resolve("backups").resolve(created.getBackup().getFileName()));

        testRunRepository.deleteAll();
        messageRepository.deleteAll();

        try (var in = new java.io.ByteArrayInputStream(bytes)) {
            var uploaded = backupService.uploadArchive(in, "history-remote.json.gz", true);
            assertTrue(uploaded.getFileName().startsWith("history-upload-"));
        }
        assertEquals(1, testRunRepository.count());
        assertEquals("Uploaded run", testRunRepository.findById("up1").orElseThrow().getTitle());
    }
}
