package com.appscreener.report.parser;

import com.appscreener.report.model.ParsedReport;
import com.appscreener.report.model.ReportType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MarkdownReportParserTest {

    private final MarkdownReportParser parser = new MarkdownReportParser();

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
    void parseFailedTestLineExtractsAllureTestResultId() {
        String text = "❌ [Broken test](https://dersecur.testops.cloud/launch/42/tree/5778)";
        ParsedReport report = parser.parse(text, "express");
        assertEquals(1, report.getTestItems().size());
        assertEquals(5778L, report.getTestItems().get(0).getAllureTestResultId());
    }
}
