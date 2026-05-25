package com.appscreener.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final ReportStorageService storage;

    @Value("${report.retention.days:365}")
    private int retentionDays;

    public RetentionScheduler(ReportStorageService storage) {
        this.storage = storage;
    }

    @Scheduled(cron = "${report.retention.cron:0 0 3 * * *}")
    public void purgeOldRecords() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = storage.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Retention: deleted {} test runs older than {} days", deleted, retentionDays);
        }
    }
}
