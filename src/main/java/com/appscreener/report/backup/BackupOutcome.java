package com.appscreener.report.backup;

import com.appscreener.report.model.BackupInfo;

public class BackupOutcome {

    private final BackupInfo backup;
    private final boolean skipped;
    private final String message;

    private BackupOutcome(BackupInfo backup, boolean skipped, String message) {
        this.backup = backup;
        this.skipped = skipped;
        this.message = message;
    }

    public static BackupOutcome created(BackupInfo info) {
        return new BackupOutcome(info, false, null);
    }

    public static BackupOutcome skipped(String reason) {
        return new BackupOutcome(null, true, reason);
    }

    public BackupInfo getBackup() {
        return backup;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public String getMessage() {
        return message;
    }
}
