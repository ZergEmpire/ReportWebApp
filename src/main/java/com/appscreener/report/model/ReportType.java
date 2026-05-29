package com.appscreener.report.model;

public enum ReportType {
    TEST_RUN_SUMMARY,
    DAILY_SUMMARY,
    FAILED_TESTS,
    PASSED_TESTS,
    SKIPPED_TESTS,
    SUCCESSFUL_SUITES,
    FAILED_SUITES,
    NO_DATA,
    /** Произвольное уведомление без Allure (маркер 📣 в тексте). */
    NOTIFICATION,
    UNKNOWN
}
