package com.appscreener.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Метаданные attachment из allure-results; содержимое — файл на диске
 * ({@code data/allure/{runId}/attachments/{source}}).
 */
@Entity
@Table(name = "allure_attachments", indexes = @Index(name = "idx_allure_attachments_run", columnList = "runId"))
public class AllureAttachmentEntity {

    /** Имя файла аттача в allure-results ({@code {uuid}-attachment.png} и т.п.). */
    @Id
    @Column(length = 128)
    private String source;

    @Column(nullable = false, length = 32)
    private String runId;

    @Column(length = 512)
    private String name;

    @Column(length = 128)
    private String contentType;

    private long sizeBytes;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }
}
