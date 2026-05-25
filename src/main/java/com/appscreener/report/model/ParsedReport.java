package com.appscreener.report.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ParsedReport {

    private String id;
    private Instant receivedAt;
    private String rawText;
    private String parseMode;
    private String testType;
    private boolean pinned;
    private ReportType reportType;
    private String title;
    private String standUrl;
    private List<String> suiteNames = new ArrayList<>();
    private String ciSuites;
    private Integer totalTests;
    private Integer passedTests;
    private Integer failedTests;
    private Integer skippedTests;
    private String executionTime;
    private String pipelineUrl;
    private String allureUrl;
    private String summaryDate;
    private List<TestLineItem> testItems = new ArrayList<>();
    private List<String> suiteLines = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getParseMode() {
        return parseMode;
    }

    public void setParseMode(String parseMode) {
        this.parseMode = parseMode;
    }

    public String getTestType() {
        return testType;
    }

    public void setTestType(String testType) {
        this.testType = testType;
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

    public List<String> getSuiteNames() {
        return suiteNames;
    }

    public void setSuiteNames(List<String> suiteNames) {
        this.suiteNames = suiteNames;
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

    public List<TestLineItem> getTestItems() {
        return testItems;
    }

    public void setTestItems(List<TestLineItem> testItems) {
        this.testItems = testItems;
    }

    public List<String> getSuiteLines() {
        return suiteLines;
    }

    public void setSuiteLines(List<String> suiteLines) {
        this.suiteLines = suiteLines;
    }

    public boolean isSuccess() {
        if (failedTests != null && failedTests > 0) {
            return false;
        }
        if (reportType == ReportType.FAILED_TESTS || reportType == ReportType.FAILED_SUITES) {
            return false;
        }
        return true;
    }
}
