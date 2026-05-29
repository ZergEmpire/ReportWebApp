package com.appscreener.report.parser;

import com.appscreener.report.model.ParsedReport;
import com.appscreener.report.model.ReportType;
import com.appscreener.report.repository.ReportCategoryRepository;
import com.appscreener.report.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkdownReportParserTest {

    @Mock
    private ReportCategoryRepository categoryRepository;

    private MarkdownReportParser parser;

    @BeforeEach
    void setUp() {
        when(categoryRepository.findAllByOrderBySortOrderAscLabelAsc()).thenReturn(List.of());
        parser = new MarkdownReportParser(new CategoryService(categoryRepository));
    }

    @Test
    void parseExpressSummaryFile() throws Exception {
        Path path = Path.of("seed-payloads/express-summary.txt");
        String text = Files.readString(path);
        ParsedReport report = parser.parse(text, "express");

        assertEquals(ReportType.TEST_RUN_SUMMARY, report.getReportType());
        assertEquals(17, report.getTotalTests());
        assertEquals(13, report.getPassedTests());
        assertEquals(4, report.getFailedTests());
        assertEquals(0, report.getSkippedTests());
        assertEquals("Набор для smoke check (appScreener)", report.getTitle());
        assertNotNull(report.getStandUrl());
        assertEquals("https://appscreener-ui01d.ast.rt-solar.ru", report.getStandUrl());
    }

    @Test
    void parseDailySummaryAndSuites() throws Exception {
        String daily = Files.readString(Path.of("seed-payloads/summary-daily.txt"));
        ParsedReport summary = parser.parse(daily, "summary");
        assertEquals(ReportType.DAILY_SUMMARY, summary.getReportType());
        assertEquals("19.05.2026", summary.getSummaryDate());
        assertEquals(156, summary.getPassedTests());
        assertEquals(23, summary.getFailedTests());
        assertEquals(8, summary.getSkippedTests());
        assertEquals(187, summary.getTotalTests());

        String okSuites = Files.readString(Path.of("seed-payloads/summary-suites-ok.txt"));
        ParsedReport ok = parser.parse(okSuites, "summary");
        assertEquals(ReportType.SUCCESSFUL_SUITES, ok.getReportType());
        assertEquals(3, ok.getTestItems().size());
        assertEquals("Patch/ApiSuiteStart.xml", ok.getTestItems().get(0).getPath());

        String failSuites = Files.readString(Path.of("seed-payloads/summary-suites-fail.txt"));
        ParsedReport fail = parser.parse(failSuites, "summary");
        assertEquals(ReportType.FAILED_SUITES, fail.getReportType());
        assertEquals(2, fail.getTestItems().size());
    }

    @Test
    void parseCiSummaryWithNewlineInsideStandBackticks() {
        String text = """
                ☑️ Результаты тестирования приложения appScreener.
                ========================
                *Стенд, где проходил автотест: \n*
                `https://appscreener-auto-ui01qa.ast.rt-solar.ru
                `
                *Название набора: \n*
                `Smoke Suite`
                *Статистика:\n*
                Всего тестов: 10
                Успешных: 7
                Проваленных (ошибка): 3
                Не запущенных (системная ошибка): 0
                """;
        ParsedReport report = parser.parse(text, "release");
        assertEquals(ReportType.TEST_RUN_SUMMARY, report.getReportType());
        assertEquals("Smoke Suite (appScreener)", report.getTitle());
        assertEquals("https://appscreener-auto-ui01qa.ast.rt-solar.ru", report.getStandUrl());
        assertEquals(10, report.getTotalTests());
        assertEquals(3, report.getFailedTests());
    }

    @Test
    void parseStandNotification() throws Exception {
        String text = java.nio.file.Files.readString(
                java.nio.file.Path.of("seed-payloads/stand-status-notification.txt"));
        ParsedReport report = parser.parse(text, "healthy");

        assertEquals(ReportType.NOTIFICATION, report.getReportType());
        assertEquals("API-стенд: плановая проверка", report.getTitle());
        assertEquals("https://api-stand.dev.example.com", report.getStandUrl());
        assertEquals(0, report.getFailedTests());
        assertEquals(1, report.getPassedTests());
        assertTrue(report.getTestItems().stream().anyMatch(i -> "Статус".equals(i.getName())));
        assertNotNull(report.getPipelineUrl());
    }

    @Test
    void parseFailedTestLineExtractsAllureTestResultId() {
        String text = "❌ [Broken test](https://dersecur.testops.cloud/launch/42/tree/5778)";
        ParsedReport report = parser.parse(text, "express");
        assertEquals(1, report.getTestItems().size());
        assertEquals(5778L, report.getTestItems().get(0).getAllureTestResultId());
    }
}
