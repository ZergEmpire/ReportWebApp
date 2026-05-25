package com.appscreener.report.service;

import com.appscreener.report.config.BackupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupProperties properties;
    private final BackupService backupService;

    public BackupScheduler(BackupProperties properties, BackupService backupService) {
        this.properties = properties;
        this.backupService = backupService;
    }

    @Scheduled(cron = "${report.backup.cron:0 0 3 * * SUN}")
    public void scheduledBackup() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            var outcome = backupService.createBackup(false);
            if (outcome.isSkipped()) {
                log.debug("Scheduled backup: {}", outcome.getMessage());
            }
        } catch (Exception e) {
            log.error("Scheduled backup failed", e);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || !properties.isOnStartup()) {
            return;
        }
        try {
            backupService.createBackup(false);
            log.info("Startup backup completed");
        } catch (Exception e) {
            log.error("Startup backup failed", e);
        }
    }
}
