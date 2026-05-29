package com.appscreener.report.backup;

import com.appscreener.report.model.ReportType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Компактный снимок истории отчётов (прогоны, сообщения, пользовательские категории).
 */
public class ReportHistorySnapshot {

    public static final int FORMAT_VERSION = 2;
    public static final int MIN_SUPPORTED_FORMAT_VERSION = 1;

    private int formatVersion = FORMAT_VERSION;
    private Instant exportedAt;
    private List<RunRecord> runs = new ArrayList<>();
    private List<MessageRecord> messages = new ArrayList<>();
    private List<CategoryRecord> categories = new ArrayList<>();

    public int getFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(int formatVersion) {
        this.formatVersion = formatVersion;
    }

    public Instant getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(Instant exportedAt) {
        this.exportedAt = exportedAt;
    }

    public List<RunRecord> getRuns() {
        return runs;
    }

    public void setRuns(List<RunRecord> runs) {
        this.runs = runs;
    }

    public List<MessageRecord> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageRecord> messages) {
        this.messages = messages;
    }

    public List<CategoryRecord> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryRecord> categories) {
        this.categories = categories;
    }

    public static class RunRecord {
        private String id;
        private String categoryCode;
        private Instant receivedAt;
        private boolean pinned;
        private ReportType reportType;
        private String title;
        private String standUrl;
        private String suiteNamesJson;
        private String ciSuites;
        private Integer totalTests;
        private Integer passedTests;
        private Integer failedTests;
        private Integer skippedTests;
        private String executionTime;
        private String pipelineUrl;
        private String allureUrl;
        private String summaryDate;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCategoryCode() {
            return categoryCode;
        }

        public void setCategoryCode(String categoryCode) {
            this.categoryCode = categoryCode;
        }

        public Instant getReceivedAt() {
            return receivedAt;
        }

        public void setReceivedAt(Instant receivedAt) {
            this.receivedAt = receivedAt;
        }

        public boolean isPinned() {
            return pinned;
        }

        public void setPinned(boolean pinned) {
            this.pinned = pinned;
        }

        public ReportType getReportType() {
            return reportType;
        }

        public void setReportType(ReportType reportType) {
            this.reportType = reportType;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getStandUrl() {
            return standUrl;
        }

        public void setStandUrl(String standUrl) {
            this.standUrl = standUrl;
        }

        public String getSuiteNamesJson() {
            return suiteNamesJson;
        }

        public void setSuiteNamesJson(String suiteNamesJson) {
            this.suiteNamesJson = suiteNamesJson;
        }

        public String getCiSuites() {
            return ciSuites;
        }

        public void setCiSuites(String ciSuites) {
            this.ciSuites = ciSuites;
        }

        public Integer getTotalTests() {
            return totalTests;
        }

        public void setTotalTests(Integer totalTests) {
            this.totalTests = totalTests;
        }

        public Integer getPassedTests() {
            return passedTests;
        }

        public void setPassedTests(Integer passedTests) {
            this.passedTests = passedTests;
        }

        public Integer getFailedTests() {
            return failedTests;
        }

        public void setFailedTests(Integer failedTests) {
            this.failedTests = failedTests;
        }

        public Integer getSkippedTests() {
            return skippedTests;
        }

        public void setSkippedTests(Integer skippedTests) {
            this.skippedTests = skippedTests;
        }

        public String getExecutionTime() {
            return executionTime;
        }

        public void setExecutionTime(String executionTime) {
            this.executionTime = executionTime;
        }

        public String getPipelineUrl() {
            return pipelineUrl;
        }

        public void setPipelineUrl(String pipelineUrl) {
            this.pipelineUrl = pipelineUrl;
        }

        public String getAllureUrl() {
            return allureUrl;
        }

        public void setAllureUrl(String allureUrl) {
            this.allureUrl = allureUrl;
        }

        public String getSummaryDate() {
            return summaryDate;
        }

        public void setSummaryDate(String summaryDate) {
            this.summaryDate = summaryDate;
        }
    }

    public static class MessageRecord {
        private String id;
        private String testRunId;
        private Instant receivedAt;
        private ReportType reportType;
        private String rawText;
        private String testItemsJson;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTestRunId() {
            return testRunId;
        }

        public void setTestRunId(String testRunId) {
            this.testRunId = testRunId;
        }

        public Instant getReceivedAt() {
            return receivedAt;
        }

        public void setReceivedAt(Instant receivedAt) {
            this.receivedAt = receivedAt;
        }

        public ReportType getReportType() {
            return reportType;
        }

        public void setReportType(ReportType reportType) {
            this.reportType = reportType;
        }

        public String getRawText() {
            return rawText;
        }

        public void setRawText(String rawText) {
            this.rawText = rawText;
        }

        public String getTestItemsJson() {
            return testItemsJson;
        }

        public void setTestItemsJson(String testItemsJson) {
            this.testItemsJson = testItemsJson;
        }
    }

    public static class CategoryRecord {
        private String code;
        private String threadId;
        private String label;
        private String icon;
        private int sortOrder;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getThreadId() {
            return threadId;
        }

        public void setThreadId(String threadId) {
            this.threadId = threadId;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public int getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}
