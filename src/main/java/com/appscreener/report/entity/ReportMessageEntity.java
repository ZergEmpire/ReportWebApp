package com.appscreener.report.entity;

import com.appscreener.report.model.ReportType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "report_messages")
public class ReportMessageEntity {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_run_id", nullable = false)
    private TestRunEntity testRun;

    @Column(nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReportType reportType;

    @Lob
    @Column(nullable = false)
    private String rawText;

    @Lob
    private String testItemsJson;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TestRunEntity getTestRun() {
        return testRun;
    }

    public void setTestRun(TestRunEntity testRun) {
        this.testRun = testRun;
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
