package com.appscreener.report.model;

/**
 * Пользовательская категория для админки (с id для удаления).
 */
public record CustomCategoryView(Long id, String threadId, String code, String label, String icon) {

    public String displayName() {
        if (icon == null || icon.isBlank()) {
            return label;
        }
        return icon + " " + label;
    }
}
