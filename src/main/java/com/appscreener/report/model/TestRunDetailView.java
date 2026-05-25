package com.appscreener.report.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TestRunDetailView {

    private String id;
    private String categoryCode;
    private String categoryLabel;
    private String categoryIcon;
    private Instant receivedAt;
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
    private List<TestLineItem> failedTestsList = new ArrayList<>();
    private List<TestLineItem> passedTestsList = new ArrayList<>();
    private List<TestLineItem> skippedTestsList = new ArrayList<>();
    private List<TestLineItem> suiteResults = new ArrayList<>();
    private List<TestLineItem> successfulSuites = new ArrayList<>();
    private List<TestLineItem> failedSuites = new ArrayList<>();

    public boolean isDailySummary() {
        return reportType == ReportType.DAILY_SUMMARY;
    }

    public boolean isSuccess() {
        return failedTests == null || failedTests == 0;
    }

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

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public void setCategoryLabel(String categoryLabel) {
        this.categoryLabel = categoryLabel;
    }

    public String getCategoryIcon() {
        return categoryIcon;
    }

    public void setCategoryIcon(String categoryIcon) {
        this.categoryIcon = categoryIcon;
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

    public List<TestLineItem> getFailedTestsList() {
        return failedTestsList;
    }

    public void setFailedTestsList(List<TestLineItem> failedTestsList) {
        this.failedTestsList = failedTestsList;
    }

    public List<TestLineItem> getPassedTestsList() {
        return passedTestsList;
    }

    public void setPassedTestsList(List<TestLineItem> passedTestsList) {
        this.passedTestsList = passedTestsList;
    }

    public List<TestLineItem> getSkippedTestsList() {
        return skippedTestsList;
    }

    public void setSkippedTestsList(List<TestLineItem> skippedTestsList) {
        this.skippedTestsList = skippedTestsList;
    }

    public List<TestLineItem> getSuiteResults() {
        return suiteResults;
    }

    public void setSuiteResults(List<TestLineItem> suiteResults) {
        this.suiteResults = suiteResults;
    }

    public List<TestLineItem> getSuccessfulSuites() {
        return successfulSuites;
    }

    public void setSuccessfulSuites(List<TestLineItem> successfulSuites) {
        this.successfulSuites = successfulSuites;
    }

    public List<TestLineItem> getFailedSuites() {
        return failedSuites;
    }

    public void setFailedSuites(List<TestLineItem> failedSuites) {
        this.failedSuites = failedSuites;
    }
}
