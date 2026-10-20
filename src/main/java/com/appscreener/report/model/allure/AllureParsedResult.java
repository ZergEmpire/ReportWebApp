package com.appscreener.report.model.allure;

/**
 * Тест из {@code *-result.json} (упрощённо: только поля, нужные для импорта прогона).
 *
 * @param uuid      uuid результата (имя файла без суффикса)
 * @param name      человекочитаемое имя теста
 * @param fullName  Class.method
 * @param status    passed / failed / broken / skipped / unknown
 * @param flaky     flaky-статус из statusDetails
 * @param message   statusDetails.message (обрезан по парсере)
 * @param start     мс epoch
 * @param stop      мс epoch
 * @param suiteName имя сюиты (container.name либо label suite/parentSuite)
 */
public record AllureParsedResult(
        String uuid,
        String name,
        String fullName,
        String status,
        boolean flaky,
        String message,
        Long start,
        Long stop,
        String suiteName) {

    public long durationMs() {
        if (start == null || stop == null || stop < start) {
            return 0;
        }
        return stop - start;
    }

    public boolean failedLike() {
        return "failed".equals(status) || "broken".equals(status);
    }

    /** Строка вида «Class#method — имя» для списков; без дублей, если name совпадает с fullName. */
    public String displayLine() {
        if (name == null || name.isBlank()) {
            return fullName != null ? fullName : uuid;
        }
        if (fullName == null || fullName.isBlank() || fullName.equals(name)) {
            return name;
        }
        return name + "  [" + fullName + "]";
    }
}
