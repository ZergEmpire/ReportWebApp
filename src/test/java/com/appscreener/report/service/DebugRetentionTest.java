package com.appscreener.report.service;

import com.appscreener.report.entity.ReportMessageEntity;
import com.appscreener.report.entity.TestRunEntity;
import com.appscreener.report.model.ReportType;
import com.appscreener.report.parser.MarkdownReportParser;
import com.appscreener.report.repository.ReportMessageRepository;
import com.appscreener.report.repository.TestRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Регрессия чистки раздела «Отладка»: старые debug-прогоны удаляются вместе с сообщениями
 * и allure-данными; свежие debug и прочие категории не затрагиваются.
 */
@DataJpaTest
@Import({ReportStorageService.class, MarkdownReportParser.class, CategoryService.class,
        AllureResultsStorageService.class, AllureResultsParser.class, AllureTestOpsService.class})
@org.springframework.boot.context.properties.EnableConfigurationProperties(
        com.appscreener.report.config.AllureTestOpsProperties.class)
class DebugRetentionTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("report.allure-results.storage-dir", () -> tempDir.resolve("allure").toString());
    }

    @Autowired
    private ReportStorageService storage;

    @Autowired
    private TestRunRepository testRunRepository;

    @Autowired
    private ReportMessageRepository messageRepository;

    @Test
    void purgeDebugOlderThanRemovesOnlyOldDebugRuns() {
        Instant now = Instant.now();
        TestRunEntity oldDebug = run("olddebugrun01", "local", now.minus(20, ChronoUnit.DAYS));
        TestRunEntity freshDebug = run("newdebugrun01", "local", now.minus(2, ChronoUnit.DAYS));
        TestRunEntity oldApi = run("oldapirun0001", "api", now.minus(20, ChronoUnit.DAYS));
        message(oldDebug, "msg-old-debug");
        message(oldApi, "msg-old-api");

        int deleted = storage.deleteDebugOlderThan(now.minus(14, ChronoUnit.DAYS));

        assertEquals(1, deleted);
        assertFalse(testRunRepository.findById("olddebugrun01").isPresent());
        assertTrue(messageRepository.findByTestRun_IdOrderByReceivedAtAsc("olddebugrun01").isEmpty());
        assertTrue(testRunRepository.findById("newdebugrun01").isPresent(), "свежий debug остаётся");
        assertTrue(testRunRepository.findById("oldapirun0001").isPresent(), "старый не-debug остаётся");
        assertEquals(1, messageRepository.findByTestRun_IdOrderByReceivedAtAsc("oldapirun0001").size());
    }

    @Test
    void purgeDebugWithoutOldRunsIsNoop() {
        run("newdebugrun02", "local", Instant.now());
        assertEquals(0, storage.deleteDebugOlderThan(Instant.now().minus(14, ChronoUnit.DAYS)));
    }

    private TestRunEntity run(String id, String categoryCode, Instant receivedAt) {
        TestRunEntity run = new TestRunEntity();
        run.setId(id);
        run.setCategoryCode(categoryCode);
        run.setReceivedAt(receivedAt);
        run.setReportType(ReportType.TEST_RUN_SUMMARY);
        run.setTitle("Прогон");
        return testRunRepository.save(run);
    }

    private void message(TestRunEntity run, String id) {
        ReportMessageEntity message = new ReportMessageEntity();
        message.setId(id);
        message.setTestRun(run);
        message.setReceivedAt(run.getReceivedAt());
        message.setReportType(ReportType.UNKNOWN);
        message.setRawText("text");
        messageRepository.save(message);
    }
}
