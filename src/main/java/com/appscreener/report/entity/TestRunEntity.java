package com.appscreener.report.entity;

import com.appscreener.report.model.ReportType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "test_runs")
public class TestRunEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 32)
    private String categoryCode;

    @Column(nullable = false)
    private Instant receivedAt;

    private boolean pinned;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportType reportType;

    @Column(length = 256)
    private String title;

    @Column(length = 512)
    private String standUrl;

    @Lob
    private String suiteNamesJson;

    @Column(length = 512)
    private String ciSuites;

    private Integer totalTests;
    private Integer passedTests;
    private Integer failedTests;
    private Integer skippedTests;

    @Column(length = 16)
    private String executionTime;

    @Column(length = 1024)
    private String pipelineUrl;

    @Column(length = 1024)
    private String allureUrl;

    @Column(length = 32)
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
