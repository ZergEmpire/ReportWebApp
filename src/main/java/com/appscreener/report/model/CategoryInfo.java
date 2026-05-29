package com.appscreener.report.model;

/**
 * Категория отчёта для UI и API (встроенная или созданная администратором).
 */
public record CategoryInfo(String threadId, String code, String label, String icon) {

    public String displayName() {
        if (icon == null || icon.isBlank()) {
            return label;
        }
        return icon + " " + label;
    }

    public boolean isAll() {
        return "all".equalsIgnoreCase(code);
    }

    public boolean isDebug() {
        return "local".equalsIgnoreCase(code);
    }

    public static CategoryInfo fromEnum(ReportCategory category) {
        return new CategoryInfo(category.getThreadId(), category.getCode(),
                category.getLabel(), category.getIcon());
    }
}
