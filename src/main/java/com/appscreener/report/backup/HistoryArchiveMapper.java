package com.appscreener.report.backup;

import com.appscreener.report.entity.ReportMessageEntity;
import com.appscreener.report.entity.ReportCategoryEntity;
import com.appscreener.report.entity.TestRunEntity;

public final class HistoryArchiveMapper {

    private HistoryArchiveMapper() {
    }

    public static ReportHistorySnapshot.RunRecord toRunRecord(TestRunEntity entity) {
        ReportHistorySnapshot.RunRecord r = new ReportHistorySnapshot.RunRecord();
        r.setId(entity.getId());
        r.setCategoryCode(entity.getCategoryCode());
        r.setReceivedAt(entity.getReceivedAt());
        r.setPinned(entity.isPinned());
        r.setReportType(entity.getReportType());
        r.setTitle(entity.getTitle());
        r.setStandUrl(entity.getStandUrl());
        r.setSuiteNamesJson(entity.getSuiteNamesJson());
        r.setCiSuites(entity.getCiSuites());
        r.setTotalTests(entity.getTotalTests());
        r.setPassedTests(entity.getPassedTests());
        r.setFailedTests(entity.getFailedTests());
        r.setSkippedTests(entity.getSkippedTests());
        r.setExecutionTime(entity.getExecutionTime());
        r.setPipelineUrl(entity.getPipelineUrl());
        r.setAllureUrl(entity.getAllureUrl());
        r.setSummaryDate(entity.getSummaryDate());
        return r;
    }

    public static ReportHistorySnapshot.MessageRecord toMessageRecord(ReportMessageEntity entity) {
        ReportHistorySnapshot.MessageRecord m = new ReportHistorySnapshot.MessageRecord();
        m.setId(entity.getId());
        m.setTestRunId(entity.getTestRun().getId());
        m.setReceivedAt(entity.getReceivedAt());
        m.setReportType(entity.getReportType());
        m.setRawText(entity.getRawText());
        m.setTestItemsJson(entity.getTestItemsJson());
        return m;
    }

    public static TestRunEntity toRunEntity(ReportHistorySnapshot.RunRecord r) {
        TestRunEntity entity = new TestRunEntity();
        entity.setId(r.getId());
        entity.setCategoryCode(r.getCategoryCode());
        entity.setReceivedAt(r.getReceivedAt());
        entity.setPinned(r.isPinned());
        entity.setReportType(r.getReportType());
        entity.setTitle(r.getTitle());
        entity.setStandUrl(r.getStandUrl());
        entity.setSuiteNamesJson(r.getSuiteNamesJson());
        entity.setCiSuites(r.getCiSuites());
        entity.setTotalTests(r.getTotalTests());
        entity.setPassedTests(r.getPassedTests());
        entity.setFailedTests(r.getFailedTests());
        entity.setSkippedTests(r.getSkippedTests());
        entity.setExecutionTime(r.getExecutionTime());
        entity.setPipelineUrl(r.getPipelineUrl());
        entity.setAllureUrl(r.getAllureUrl());
        entity.setSummaryDate(r.getSummaryDate());
        return entity;
    }

    public static ReportMessageEntity toMessageEntity(ReportHistorySnapshot.MessageRecord m, TestRunEntity run) {
        ReportMessageEntity entity = new ReportMessageEntity();
        entity.setId(m.getId());
        entity.setTestRun(run);
        entity.setReceivedAt(m.getReceivedAt());
        entity.setReportType(m.getReportType());
        entity.setRawText(m.getRawText());
        entity.setTestItemsJson(m.getTestItemsJson());
        return entity;
    }

    public static ReportHistorySnapshot.CategoryRecord toCategoryRecord(ReportCategoryEntity entity) {
        ReportHistorySnapshot.CategoryRecord c = new ReportHistorySnapshot.CategoryRecord();
        c.setCode(entity.getCode());
        c.setThreadId(entity.getThreadId());
        c.setLabel(entity.getLabel());
        c.setIcon(entity.getIcon());
        c.setSortOrder(entity.getSortOrder());
        return c;
    }

    public static ReportCategoryEntity toCategoryEntity(ReportHistorySnapshot.CategoryRecord record) {
        ReportCategoryEntity entity = new ReportCategoryEntity();
        entity.setCode(record.getCode());
        entity.setThreadId(record.getThreadId());
        entity.setLabel(record.getLabel());
        entity.setIcon(record.getIcon());
        entity.setSortOrder(record.getSortOrder());
        return entity;
    }
}
