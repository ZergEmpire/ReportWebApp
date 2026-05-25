package com.appscreener.report.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Категории как в Telegram-группе AUTOTEST AS/DS (message_thread_id → testType).
 */
public enum ReportCategory {

    /** Все прогоны, кроме {@link #DEBUG} (отладка только в своём разделе). */
    ALL(null, "all", "Все", ""),
    HEALTHY("2152", "healthy", "Health monitor", "✅"),
    SUMMARY("2156", "summary", "Summary", "📊"),
    UI("4", "ui", "UI", "🖥️"),
    API("2", "api", "API", "⚙️"),
    EXPRESS("2154", "express", "Express тесты", "🔥"),
    GENERAL("1", "general", "General", "#"),
    UPDATES("2148", "updates", "Обновление базы SCA/SCS", "🗒️"),
    AI("2158", "alt", "AI", "🤖"),
    RELEASE("2913", "release", "Release/Patch", "📦"),
    /** Локальные прогоны и отчёты отладочного TG-чата (вместо message_thread_id — тег local / @localStart). */
    DEBUG("local", "local", "Отладка", "🛠️");

    private final String threadId;
    private final String code;
    private final String label;
    private final String icon;

    ReportCategory(String threadId, String code, String label, String icon) {
        this.threadId = threadId;
        this.code = code;
        this.label = label;
        this.icon = icon;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public String displayName() {
        if (icon == null || icon.isBlank()) {
            return label;
        }
        return icon + " " + label;
    }

    public static Optional<ReportCategory> fromThreadId(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return Optional.of(GENERAL);
        }
        String normalized = threadId.trim();
        if ("localstart".equalsIgnoreCase(normalized) || "debug".equalsIgnoreCase(normalized)) {
            return Optional.of(DEBUG);
        }
        return Arrays.stream(values())
                .filter(c -> c != ALL && normalized.equals(c.threadId))
                .findFirst();
    }

    public static Optional<ReportCategory> fromCode(String code) {
        if (code == null || code.isBlank() || "all".equalsIgnoreCase(code)) {
            return Optional.of(ALL);
        }
        return Arrays.stream(values())
                .filter(c -> c.code.equalsIgnoreCase(code))
                .findFirst();
    }

    public static List<ReportCategory> navigable() {
        return List.of(
                ALL, HEALTHY, SUMMARY, UI, API, EXPRESS,
                GENERAL, UPDATES, AI, RELEASE, DEBUG
        );
    }
}
