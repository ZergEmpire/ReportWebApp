package com.appscreener.report.model.allure;

import java.util.List;

/**
 * Итог разбора каталога/архива allure-results для импорта прогона.
 *
 * @param results   тесты, отсортированные по времени старта
 * @param suites    имена сьют (из containers), в порядке следования
 * @param minStart  самый ранний start среди результатов (мс epoch)
 * @param maxStop   самый поздний stop среди результатов (мс epoch)
 */
public record AllureParsedRun(
        List<AllureParsedResult> results,
        List<String> suites,
        long minStart,
        long maxStop) {

    public int countByStatus(String status) {
        return (int) results.stream().filter(r -> status.equals(r.status())).count();
    }

    public int passedCount() {
        return countByStatus("passed");
    }

    /** failed + broken — как «Проваленных (ошибка)» в сводке TelegramListener. */
    public int failedCount() {
        return countByStatus("failed") + countByStatus("broken");
    }

    /** skipped + unknown. */
    public int skippedCount() {
        return countByStatus("skipped") + countByStatus("unknown");
    }
}
