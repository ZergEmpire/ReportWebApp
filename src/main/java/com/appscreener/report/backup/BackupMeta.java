package com.appscreener.report.backup;

import java.time.Instant;

public class BackupMeta {

    private int formatVersion = ReportHistorySnapshot.FORMAT_VERSION;
    private String archiveFileName;
    private Instant createdAt;
    private int runCount;
    private int messageCount;
    private Instant latestActivity;

    public int getFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(int formatVersion) {
        this.formatVersion = formatVersion;
    }

    public String getArchiveFileName() {
        return archiveFileName;
    }

    public void setArchiveFileName(String archiveFileName) {
        this.archiveFileName = archiveFileName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getRunCount() {
        return runCount;
    }

    public void setRunCount(int runCount) {
        this.runCount = runCount;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public Instant getLatestActivity() {
        return latestActivity;
    }

    public void setLatestActivity(Instant latestActivity) {
        this.latestActivity = latestActivity;
    }

    public boolean isSameAs(int runs, int messages, Instant latest) {
        return runCount == runs
                && messageCount == messages
                && ((latestActivity == null && latest == null)
                || (latestActivity != null && latestActivity.equals(latest)));
    }
}
