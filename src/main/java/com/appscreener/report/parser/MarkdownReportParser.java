package com.appscreener.report.parser;

import com.appscreener.report.model.CategoryInfo;
import com.appscreener.report.model.ParsedReport;
import com.appscreener.report.model.ReportCategory;
import com.appscreener.report.model.ReportType;
import com.appscreener.report.model.TestLineItem;
import com.appscreener.report.service.CategoryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownReportParser {

    private final CategoryService categoryService;

    public MarkdownReportParser(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern INT_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern TEST_LINE_PATTERN = Pattern.compile("^(✅|❌|➡️|⚠️|ℹ️)\\s+(.+)$");
    private static final Pattern NOTIFICATION_LABEL = Pattern.compile("^\\*([^*]+):\\*\\s*(.*)$");
    private static final Pattern NOTIFICATION_STATUS_LINE = Pattern.compile("^(✅|❌|⚠️|ℹ️)\\s+(.+)$");
    private static final Pattern ALLURE_TEST_RESULT_ID = Pattern.compile(
            "(?:/launch/\\d+/tree/|/testresult/)(\\d+)", Pattern.CASE_INSENSITIVE);

    public ParsedReport parse(String rawText, String testTypeLabel) {
        ParsedReport report = new ParsedReport();
        report.setRawText(rawText);
        report.setTestType(testTypeLabel != null ? testTypeLabel : resolveTestTypeLabel(null));
        report.setReportType(detectType(rawText));
        report.setTitle(extractTitle(rawText, report.getReportType()));

        switch (report.getReportType()) {
            case TEST_RUN_SUMMARY -> parseTestRunSummary(rawText, report);
            case DAILY_SUMMARY -> parseDailySummary(rawText, report);
            case FAILED_TESTS, PASSED_TESTS, SKIPPED_TESTS -> parseTestList(rawText, report);
            case SUCCESSFUL_SUITES, FAILED_SUITES -> parseSuiteList(rawText, report);
            case NOTIFICATION -> parseNotification(rawText, report);
            case NO_DATA -> report.setTitle("Нет данных Summary");
            default -> {
            }
        }

        extractLinks(rawText, report);
        return report;
    }

    private ReportType detectType(String text) {
        if (isNotification(text)) {
            return ReportType.NOTIFICATION;
        }
        if (text.contains("🚫 Данные для") && text.contains("Summary")) {
            return ReportType.NO_DATA;
        }
        if (text.contains("📝") && text.contains("SUMMARY")) {
            return ReportType.DAILY_SUMMARY;
        }
        if (text.contains("Результаты тестирования приложения appScreener")
                || text.contains("☑️ Результаты тестирования")
                || text.contains("✅ Результаты тестирования")) {
            return ReportType.TEST_RUN_SUMMARY;
        }
        if (text.contains("Упавшие тесты с ошибкой")) {
            return ReportType.FAILED_TESTS;
        }
        if (text.contains("Успешно пройденные тесты")) {
            return ReportType.PASSED_TESTS;
        }
        if (text.contains("не были запущены из-за системной ошибки")) {
            return ReportType.SKIPPED_TESTS;
        }
        if (text.contains("Успешно пройденные наборы")) {
            return ReportType.SUCCESSFUL_SUITES;
        }
        if (text.contains("Наборы, пройденные с ошибками")) {
            return ReportType.FAILED_SUITES;
        }
        return detectTestListByIcons(text);
    }

    /** Продолжение списка тестов без заголовка секции (2+ батч из TelegramListener). */
    private ReportType detectTestListByIcons(String text) {
        if (text.contains("Результаты тестирования") || text.contains("SUMMARY")) {
            return ReportType.UNKNOWN;
        }
        int failed = countLinesWithIcon(text, "❌");
        int passed = countLinesWithIcon(text, "✅");
        int skipped = countLinesWithIcon(text, "➡️");
        if (failed + passed + skipped == 0) {
            return ReportType.UNKNOWN;
        }
        if (text.contains("Упавшие тесты")) {
            return ReportType.FAILED_TESTS;
        }
        if (text.contains("Успешно пройденные тесты")) {
            return ReportType.PASSED_TESTS;
        }
        if (text.contains("не были запущены")) {
            return ReportType.SKIPPED_TESTS;
        }
        if (failed > 0 && passed == 0 && skipped == 0) {
            return ReportType.FAILED_TESTS;
        }
        if (passed > 0 && failed == 0 && skipped == 0) {
            return ReportType.PASSED_TESTS;
        }
        if (skipped > 0 && failed == 0 && passed == 0) {
            return ReportType.SKIPPED_TESTS;
        }
        if (failed >= passed && failed >= skipped) {
            return ReportType.FAILED_TESTS;
        }
        if (passed >= skipped) {
            return ReportType.PASSED_TESTS;
        }
        return ReportType.SKIPPED_TESTS;
    }

    private int countLinesWithIcon(String text, String icon) {
        int count = 0;
        for (String line : text.split("\n")) {
            if (line.trim().startsWith(icon)) {
                count++;
            }
        }
        return count;
    }

    private String extractTitle(String text, ReportType type) {
        return switch (type) {
            case TEST_RUN_SUMMARY -> "Результаты тестирования appScreener";
            case DAILY_SUMMARY -> "Daily Summary";
            case FAILED_TESTS -> "Упавшие тесты";
            case PASSED_TESTS -> "Успешные тесты";
            case SKIPPED_TESTS -> "Пропущенные тесты";
            case SUCCESSFUL_SUITES -> "Успешные наборы";
            case FAILED_SUITES -> "Наборы с ошибками";
            case NO_DATA -> "Summary — нет данных";
            case NOTIFICATION -> "Уведомление";
            default -> "Отчёт";
        };
    }

    private boolean isNotification(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.startsWith("📣");
            }
        }
        return false;
    }

    /**
     * Формат произвольного уведомления (без Allure):
     * <pre>
     * 📣 Заголовок
     * ========================
     * *Стенд:*
     * `https://stand.example.com`
     * *Статус:*
     * ✅ Сервис отвечает
     * </pre>
     */
    private void parseNotification(String text, ParsedReport report) {
        String normalized = text.replace("\r\n", "\n");
        for (String line : normalized.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("📣")) {
                String title = trimmed.substring("📣".length()).trim();
                if (!title.isEmpty()) {
                    report.setTitle(title);
                }
                break;
            }
        }
        if (report.getTitle() == null || report.getTitle().isBlank()) {
            report.setTitle("Уведомление");
        }

        String[] currentLabel = {null};
        StringBuilder fieldBody = new StringBuilder();

        for (String line : normalized.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("📣")
                    || trimmed.startsWith("====") || trimmed.startsWith("----")) {
                continue;
            }
            Matcher status = NOTIFICATION_STATUS_LINE.matcher(stripMarkdown(trimmed));
            if (status.matches()) {
                flushNotificationField(report, currentLabel, fieldBody);
                TestLineItem item = new TestLineItem();
                item.setIcon(status.group(1));
                String rest = status.group(2).trim();
                Matcher link = LINK_PATTERN.matcher(rest);
                if (link.find()) {
                    item.setName(link.group(1));
                    item.setUrl(link.group(2));
                } else {
                    item.setName(rest);
                }
                report.getTestItems().add(item);
                continue;
            }
            Matcher label = NOTIFICATION_LABEL.matcher(trimmed);
            if (label.matches()) {
                flushNotificationField(report, currentLabel, fieldBody);
                currentLabel[0] = label.group(1).trim();
                String inline = label.group(2).trim();
                if (!inline.isEmpty()) {
                    fieldBody.append(inline);
                }
                continue;
            }
            if (currentLabel[0] != null) {
                if (!fieldBody.isEmpty()) {
                    fieldBody.append('\n');
                }
                fieldBody.append(trimmed);
            }
        }
        flushNotificationField(report, currentLabel, fieldBody);

        int problems = 0;
        int ok = 0;
        for (TestLineItem item : report.getTestItems()) {
            if ("❌".equals(item.getIcon()) || "⚠️".equals(item.getIcon())) {
                problems++;
            } else if ("✅".equals(item.getIcon())) {
                ok++;
            }
        }
        report.setFailedTests(problems);
        report.setPassedTests(ok);
    }

    private void flushNotificationField(ParsedReport report, String[] currentLabel, StringBuilder fieldBody) {
        if (currentLabel[0] == null) {
            return;
        }
        String value = stripMarkdown(fieldBody.toString().trim());
        if (value.isEmpty()) {
            currentLabel[0] = null;
            fieldBody.setLength(0);
            return;
        }
        if (isStandLabel(currentLabel[0])) {
            String url = firstUrlToken(value);
            if (url != null) {
                report.setStandUrl(url);
            }
        }
        TestLineItem field = new TestLineItem();
        field.setName(currentLabel[0]);
        field.setMeta(value);
        Matcher link = LINK_PATTERN.matcher(value);
        if (link.find()) {
            field.setUrl(link.group(2));
            field.setMeta(link.group(1));
        }
        report.getTestItems().add(field);
        currentLabel[0] = null;
        fieldBody.setLength(0);
    }

    private static boolean isStandLabel(String label) {
        return label != null && label.toLowerCase().contains("стенд");
    }

    private static String firstUrlToken(String value) {
        Matcher backtick = Pattern.compile("`(https?://[^`]+)`").matcher(value);
        if (backtick.find()) {
            return backtick.group(1).trim();
        }
        Matcher plain = Pattern.compile("(https?://\\S+)").matcher(value);
        if (plain.find()) {
            return plain.group(1).replaceAll("[)\\],.]+$", "");
        }
        return value.startsWith("http") ? value.trim() : null;
    }

    private void parseTestRunSummary(String text, ParsedReport report) {
        String normalized = text.replace("\r\n", "\n");
        report.setStandUrl(parseStandUrl(normalized));
        String suitesBlock = extractBacktickBlock(normalized, "Название набора");
        if (suitesBlock != null) {
            for (String line : suitesBlock.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    report.getSuiteNames().add(trimmed.replaceFirst("^-\\s*", ""));
                }
            }
        }
        if (!report.getSuiteNames().isEmpty()) {
            report.setTitle(report.getSuiteNames().get(0) + " (appScreener)");
        }
        report.setCiSuites(extractBacktickBlock(normalized, "CI_SUITES"));
        if (report.getCiSuites() == null) {
            report.setCiSuites(extractBacktickBlock(normalized, "CI_SUITES для запуска Pipeline"));
        }
        report.setTotalTests(extractStat(normalized, "Всего тестов"));
        report.setPassedTests(extractStat(normalized, "Успешных"));
        report.setFailedTests(extractStat(normalized, "Проваленных"));
        report.setSkippedTests(extractStat(normalized, "Не запущенных"));
        Matcher timeMatcher = Pattern.compile("Время прохождения[^:]*:\\s*(\\d{2}:\\d{2}:\\d{2})").matcher(normalized);
        if (timeMatcher.find()) {
            report.setExecutionTime(timeMatcher.group(1));
        }
    }

    private void parseDailySummary(String text, ParsedReport report) {
        Matcher dateMatcher = Pattern.compile("SUMMARY\\*?\\s*\\(([^)]+)\\)").matcher(text);
        if (dateMatcher.find()) {
            report.setSummaryDate(dateMatcher.group(1));
        }
        report.setPassedTests(extractIntAfterLabel(text, "Всего пройденных тестов:"));
        report.setFailedTests(extractIntAfterLabel(text, "Всего проваленных тестов:"));
        report.setSkippedTests(extractIntAfterLabel(text, "Всего пропущенных тестов:"));
        int passed = report.getPassedTests() != null ? report.getPassedTests() : 0;
        int failed = report.getFailedTests() != null ? report.getFailedTests() : 0;
        int skipped = report.getSkippedTests() != null ? report.getSkippedTests() : 0;
        report.setTotalTests(passed + failed + skipped);
        Matcher timeMatcher = Pattern.compile("Время прохождения всех наборов:\\s*(\\d{2}:\\d{2}:\\d{2})").matcher(text);
        if (timeMatcher.find()) {
            report.setExecutionTime(timeMatcher.group(1));
        }
    }

    private void parseTestList(String text, ParsedReport report) {
        for (String line : text.split("\n")) {
            String trimmed = stripMarkdown(line.trim());
            Matcher m = TEST_LINE_PATTERN.matcher(trimmed);
            if (m.find()) {
                TestLineItem item = new TestLineItem();
                item.setIcon(m.group(1));
                String rest = m.group(2).trim();
                Matcher link = LINK_PATTERN.matcher(rest);
                if (link.find()) {
                    item.setName(link.group(1));
                    item.setUrl(link.group(2));
                    parseAllureTestResultId(item.getUrl()).ifPresent(item::setAllureTestResultId);
                } else {
                    item.setName(rest);
                }
                report.getTestItems().add(item);
            }
        }
    }

    private void parseSuiteList(String text, ParsedReport report) {
        String normalized = text.replace("\r\n", "\n");
        for (String block : normalized.split("\n\n")) {
            String trimmed = block.trim();
            if (trimmed.isEmpty() || (!trimmed.contains("✅") && !trimmed.contains("❌"))) {
                continue;
            }
            TestLineItem item = new TestLineItem();
            for (String line : trimmed.split("\n")) {
                String l = line.trim();
                if (l.startsWith("✅") || l.startsWith("❌")) {
                    item.setIcon(l.startsWith("✅") ? "✅" : "❌");
                    String afterIcon = l.substring(1).trim();
                    if (!afterIcon.isEmpty() && !afterIcon.startsWith("[")) {
                        item.setMeta(stripMarkdown(afterIcon));
                    }
                }
                Matcher link = LINK_PATTERN.matcher(l);
                if (link.find()) {
                    item.setName(link.group(1));
                    item.setUrl(link.group(2));
                }
                Matcher path = Pattern.compile("`([^`]+)`").matcher(l);
                if (path.find()) {
                    item.setPath(path.group(1));
                }
            }
            if (item.getName() != null) {
                report.getTestItems().add(item);
            }
        }
    }

    private void extractLinks(String text, ParsedReport report) {
        Matcher matcher = LINK_PATTERN.matcher(text);
        while (matcher.find()) {
            String label = matcher.group(1);
            String url = matcher.group(2);
            if (label.contains("Pipeline") || url.contains("gitlab")) {
                report.setPipelineUrl(url);
            } else if (label.contains("Allure") || url.contains("testops")) {
                report.setAllureUrl(url);
            } else if (report.getReportType() == ReportType.NOTIFICATION && report.getPipelineUrl() == null) {
                report.setPipelineUrl(url);
            }
        }
    }

    private String parseStandUrl(String text) {
        String stand = extractBacktickBlock(text, "Стенд, где проходил автотест");
        if (stand == null) {
            stand = extractBacktickBlock(text, "Стенд");
        }
        if (stand != null) {
            return normalizeUrlToken(stand);
        }
        Matcher urlMatcher = Pattern.compile("`(https?://[^`]+)`").matcher(text);
        if (urlMatcher.find()) {
            return normalizeUrlToken(urlMatcher.group(1));
        }
        return null;
    }

    private static String normalizeUrlToken(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("[\\r\\n]+", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String extractBacktickBlock(String text, String labelPart) {
        int idx = text.indexOf(labelPart);
        if (idx < 0) {
            return null;
        }
        int tickStart = text.indexOf('`', idx);
        if (tickStart < 0) {
            return null;
        }
        int tickEnd = text.indexOf('`', tickStart + 1);
        if (tickEnd < 0) {
            return null;
        }
        return text.substring(tickStart + 1, tickEnd).trim();
    }

    private Integer extractIntAfterLabel(String text, String label) {
        int idx = text.indexOf(label);
        if (idx < 0) {
            return null;
        }
        String after = text.substring(idx + label.length()).trim();
        Matcher m = INT_PATTERN.matcher(after);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private Integer extractStat(String text, String labelPrefix) {
        Matcher m = Pattern.compile(Pattern.quote(labelPrefix) + "[^:]*:\\s*(\\d+)").matcher(text);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return extractIntAfterLabel(text, labelPrefix + ":");
    }

    private String stripMarkdown(String s) {
        return s.replace("*", "").replace("`", "");
    }

    private static Optional<Long> parseAllureTestResultId(String url) {
        if (url == null) {
            return Optional.empty();
        }
        Matcher m = ALLURE_TEST_RESULT_ID.matcher(url);
        if (m.find()) {
            return Optional.of(Long.parseLong(m.group(1)));
        }
        return Optional.empty();
    }

    public String resolveTestTypeLabel(String threadId) {
        return categoryService.resolveByThreadId(threadId)
                .filter(c -> !c.isAll())
                .map(CategoryInfo::code)
                .orElse(ReportCategory.GENERAL.getCode());
    }
}
