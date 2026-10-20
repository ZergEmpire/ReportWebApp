package com.appscreener.report.model.allure;

import java.util.List;

/**
 * Модель страницы теста allure-прогона (/report/{runId}/allure/{resultId}).
 */
public class AllureTestPageView {

    private String runId;
    private String runTitle;
    private String resultId;
    private String name;
    private String fullName;
    private String status;
    private String statusMessage;
    private String statusTrace;
    private Long startMs;
    private Long stopMs;
    private long durationMs;
    private String suiteName;
    private boolean flaky;
    /** Аннотации: {@code @Owner}, {@code @Severity}, {@code @Epic}/@Feature/@Story, {@code @Tags}. */
    private String owner;
    private String severity;
    private String epic;
    private String feature;
    private String story;
    private List<String> tags = List.of();
    /** {@code @Description}. */
    private String description;
    /** {@code @Link}/@Issue/@TmsLink. */
    private List<AllureLink> links = List.of();
    private List<AllureStepNode> steps = List.of();
    private List<StepRow> stepRows = List.of();
    private List<AllureParam> parameters = List.of();
    private List<AttachmentView> attachments = List.of();

    /** Статусная иконка в стиле сводок TelegramListener. */
    public String getStatusIcon() {
        return switch (status == null ? "unknown" : status) {
            case "passed" -> "✅";
            case "failed" -> "❌";
            case "broken" -> "⚠️";
            default -> "➡️";
        };
    }

    public java.time.Instant getStartInstant() {
        return startMs == null || startMs <= 0 ? null : java.time.Instant.ofEpochMilli(startMs);
    }

    public String getDurationLabel() {
        if (durationMs <= 0) {
            return null;
        }
        if (durationMs < 1000) {
            return durationMs + " мс";
        }
        long seconds = durationMs / 1000;
        if (seconds < 60) {
            return String.format("%.1f с", durationMs / 1000.0);
        }
        long minutes = seconds / 60;
        return String.format("%02d:%02d", minutes, seconds % 60);
    }

    public String getStatusLabel() {
        return switch (status == null ? "unknown" : status) {
            case "passed" -> "passed";
            case "failed" -> "failed (assertion)";
            case "broken" -> "broken (exception)";
            case "skipped" -> "skipped";
            default -> "unknown";
        };
    }

    /** CSS-класс бейджа severity: blocker/critical — красные, major — жёлтый, прочие — серые. */
    public String getSeverityClass() {
        return switch (severity == null ? "" : severity.toLowerCase()) {
            case "blocker" -> "severity-blocker";
            case "critical" -> "severity-critical";
            case "major" -> "severity-major";
            default -> "severity-minor";
        };
    }

    public static class AttachmentView {

        private final String source;
        private final String name;
        private final String contentType;
        private final long sizeBytes;
        /** Текстовое содержимое для inline-показа (только text/*, ограничено по размеру). */
        private final String inlineText;

        public AttachmentView(String source, String name, String contentType, long sizeBytes, String inlineText) {
            this.source = source;
            this.name = name;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.inlineText = inlineText;
        }

        public String getSource() {
            return source;
        }

        public String getName() {
            return name;
        }

        public String getContentType() {
            return contentType;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getInlineText() {
            return inlineText;
        }

        public String getUrl() {
            return "/api/allure/attachments/" + source;
        }

        public boolean isImage() {
            return contentType != null && contentType.startsWith("image/");
        }

        public boolean isVideo() {
            return contentType != null && contentType.startsWith("video/");
        }

        public boolean isHtml() {
            return contentType != null && contentType.contains("html");
        }

        public boolean isInlineText() {
            return inlineText != null;
        }
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getRunTitle() {
        return runTitle;
    }

    public void setRunTitle(String runTitle) {
        this.runTitle = runTitle;
    }

    public String getResultId() {
        return resultId;
    }

    public void setResultId(String resultId) {
        this.resultId = resultId;
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

    public String getStatusTrace() {
        return statusTrace;
    }

    public void setStatusTrace(String statusTrace) {
        this.statusTrace = statusTrace;
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

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
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

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getEpic() {
        return epic;
    }

    public void setEpic(String epic) {
        this.epic = epic;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getStory() {
        return story;
    }

    public void setStory(String story) {
        this.story = story;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<AllureLink> getLinks() {
        return links;
    }

    public void setLinks(List<AllureLink> links) {
        this.links = links;
    }

    public List<AllureStepNode> getSteps() {
        return steps;
    }

    public void setSteps(List<AllureStepNode> steps) {
        this.steps = steps;
    }

    public List<StepRow> getStepRows() {
        return stepRows;
    }

    public void setStepRows(List<StepRow> stepRows) {
        this.stepRows = stepRows;
    }

    public List<AllureParam> getParameters() {
        return parameters;
    }

    public void setParameters(List<AllureParam> parameters) {
        this.parameters = parameters;
    }

    public List<AttachmentView> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentView> attachments) {
        this.attachments = attachments;
    }
}
