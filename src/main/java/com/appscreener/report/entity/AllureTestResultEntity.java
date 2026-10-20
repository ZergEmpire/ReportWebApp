package com.appscreener.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Тест из allure-results ({@code *-result.json}); raw JSON лежит на диске
 * ({@code data/allure/{runId}/results/{uuid}-result.json}), в БД — только индексные поля.
 */
@Entity
@Table(name = "allure_test_results", indexes = @Index(name = "idx_allure_results_run", columnList = "runId"))
public class AllureTestResultEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 32)
    private String runId;

    @Column(nullable = false)
    private int sortIndex;

    @Column(length = 1024)
    private String name;

    @Lob
    private String fullName;

    /** passed / failed / broken / skipped / unknown. */
    @Column(nullable = false, length = 16)
    private String status;

    @Lob
    private String statusMessage;

    private Long startMs;
    private Long stopMs;

    @Column(length = 512)
    private String suiteName;

    private boolean flaky;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public int getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(int sortIndex) {
        this.sortIndex = sortIndex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Long getStartMs() {
        return startMs;
    }

    public void setStartMs(Long startMs) {
        this.startMs = startMs;
    }

    public Long getStopMs() {
        return stopMs;
    }

    public void setStopMs(Long stopMs) {
        this.stopMs = stopMs;
    }

    public String getSuiteName() {
        return suiteName;
    }

    public void setSuiteName(String suiteName) {
        this.suiteName = suiteName;
    }

    public boolean isFlaky() {
        return flaky;
    }

    public void setFlaky(boolean flaky) {
        this.flaky = flaky;
    }

    public boolean isFailedLike() {
        return "failed".equals(status) || "broken".equals(status);
    }
}
