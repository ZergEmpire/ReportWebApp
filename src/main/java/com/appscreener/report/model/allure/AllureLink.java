package com.appscreener.report.model.allure;

/**
 * Ссылка из {@code links} результата ({@code @Link} / {@code @Issue} / {@code @TmsLink}).
 */
public record AllureLink(String name, String url, String type) {

    /** Человекочитаемый тип для UI. */
    public String typeLabel() {
        if (type == null || type.isBlank() || "link".equalsIgnoreCase(type)) {
            return "ссылка";
        }
        if ("issue".equalsIgnoreCase(type)) {
            return "issue";
        }
        if ("tms".equalsIgnoreCase(type)) {
            return "TMS";
        }
        return type;
    }
}
