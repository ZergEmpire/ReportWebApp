package com.appscreener.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Регулярная чистка хранилища:
 * <ul>
 *   <li>общий retention — всё старше {@code report.retention.days} (по умолчанию 365);</li>
 *   <li>раздел «Отладка» — проверяется ежедневно, удаляются debug-прогоны старше
 *       {@code report.debug-retention.days} (по умолчанию 14): в разделе ничего не живёт
 *       дольше двух недель, свежие локальные прогоны не затрагиваются.</li>
 * </ul>
 */
@Component
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final ReportStorageService storage;

    @Value("${report.retention.days:365}")
    private int retentionDays;

    /** Возраст debug-прогонов для удаления; 0 или меньше — чистка «Отладки» отключена. */
    @Value("${report.debug-retention.days:14}")
    private int debugRetentionDays;

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

    @Scheduled(cron = "${report.debug-retention.cron:0 20 3 * * *}")
    public void purgeDebugSection() {
        if (debugRetentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(debugRetentionDays, ChronoUnit.DAYS);
        int deleted = storage.deleteDebugOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Debug retention: deleted {} runs from «Отладка» older than {} days",
                    deleted, debugRetentionDays);
        }
    }
}
