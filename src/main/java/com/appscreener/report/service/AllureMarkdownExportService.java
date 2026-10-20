package com.appscreener.report.service;

import com.appscreener.report.entity.AllureTestResultEntity;
import com.appscreener.report.entity.TestRunEntity;
import com.appscreener.report.model.allure.AllureTestPageView;
import com.appscreener.report.model.allure.StepRow;
import com.appscreener.report.repository.AllureTestResultRepository;
import com.appscreener.report.repository.TestRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Выгрузка allure-данных прогона/теста в Markdown для дальнейшей обработки через ИИ:
 * сводный отчёт прогона со ссылками на каждый тест и детальный отчёт по тесту
 * с деревом шагов, параметрами и содержимым аттачей. Длинные строки капятся,
 * чтобы гигантские параметры/трейсы не раздували файл.
 */
@Service
public class AllureMarkdownExportService {

    private static final int PARAM_CAP = 400;
    private static final int MESSAGE_CAP = 2000;
    private static final int TRACE_CAP = 10_000;
    private static final int ATTACHMENT_TEXT_CAP = 6000;

    private final TestRunRepository testRunRepository;
    private final AllureTestResultRepository testResultRepository;
    private final AllureResultsStorageService storageService;
    private final CategoryService categoryService;

    public AllureMarkdownExportService(TestRunRepository testRunRepository,
                                       AllureTestResultRepository testResultRepository,
                                       AllureResultsStorageService storageService,
                                       CategoryService categoryService) {
        this.testRunRepository = testRunRepository;
        this.testResultRepository = testResultRepository;
        this.storageService = storageService;
        this.categoryService = categoryService;
    }

    /** Сводный .md прогона: сводка → упавшие детально → списки с ссылками на каждый тест. */
    @Transactional(readOnly = true)
    public String exportRun(String runId, String baseUrl) {
        TestRunEntity run = testRunRepository.findById(runId).orElse(null);
        if (run == null) {
            return null;
        }
        List<AllureTestResultEntity> results = testResultRepository.findByRunIdOrderBySortIndexAsc(runId);
        String runUrl = baseUrl + "/report/" + runId;

        StringBuilder md = new StringBuilder(16_384);
        md.append("# Отчёт прогона: ").append(run.getTitle() != null ? run.getTitle() : runId).append("\n\n");
        md.append("- **Идентификатор прогона**: `").append(runId).append("`\n");
        md.append("- **Онлайн-отчёт**: ").append(runUrl).append("\n");
        md.append("- **Категория**: ").append(categoryLabel(run)).append("\n");
        md.append("- **Дата получения**: ").append(run.getReceivedAt()).append("\n");
        if (notBlank(run.getStandUrl())) {
            md.append("- **Стенд**: ").append(run.getStandUrl()).append("\n");
        }
        if (notBlank(run.getCiSuites())) {
            md.append("- **CI_SUITES**: `").append(run.getCiSuites()).append("`\n");
        }
        if (notBlank(run.getPipelineUrl())) {
            md.append("- **Pipeline**: ").append(run.getPipelineUrl()).append("\n");
        }
        List<String> suites = com.appscreener.report.util.JsonUtil.stringListFromJson(run.getSuiteNamesJson());
        if (!suites.isEmpty()) {
            md.append("- **Наборы (suites)**: ").append(String.join("; ", suites)).append("\n");
        }

        md.append("\n## Сводка\n\n");
        md.append("| Метрика | Значение |\n|---|---|\n");
        md.append("| Всего тестов | ").append(nz(run.getTotalTests())).append(" |\n");
        md.append("| Успешных | ").append(nz(run.getPassedTests())).append(" |\n");
        md.append("| Проваленных (ошибка) | ").append(nz(run.getFailedTests())).append(" |\n");
        md.append("| Не запущенных (системная ошибка) | ").append(nz(run.getSkippedTests())).append(" |\n");
        if (notBlank(run.getExecutionTime())) {
            md.append("| Длительность | ").append(run.getExecutionTime()).append(" |\n");
        }

        List<AllureTestResultEntity> failedLike = results.stream().filter(AllureTestResultEntity::isFailedLike).toList();
        List<AllureTestResultEntity> passed = results.stream().filter(r -> "passed".equals(r.getStatus())).toList();
        List<AllureTestResultEntity> rest = results.stream()
                .filter(r -> !r.isFailedLike() && !"passed".equals(r.getStatus())).toList();

        md.append("\n## ❌ Упавшие тесты (").append(failedLike.size()).append(")\n");
        for (AllureTestResultEntity r : failedLike) {
            md.append("\n### ").append("⚠️".equals(icon(r)) ? "⚠️" : "❌").append(" ")
                    .append(text(r.getName(), r.getFullName())).append("\n\n");
            md.append("- **Статус**: `").append(r.getStatus()).append("`\n");
            if (notBlank(r.getFullName())) {
                md.append("- **Полное имя**: `").append(r.getFullName()).append("`\n");
            }
            if (notBlank(r.getSuiteName())) {
                md.append("- **Сюита**: ").append(r.getSuiteName()).append("\n");
            }
            if (notBlank(r.getStatusMessage())) {
                md.append("- **Ошибка**: ").append(quote(cap(r.getStatusMessage(), MESSAGE_CAP))).append("\n");
            }
            md.append("- **Страница теста**: [открыть](").append(testUrl(baseUrl, runId, r.getId())).append(")\n");
        }
        if (failedLike.isEmpty()) {
            md.append("\n_Нет упавших тестов._\n");
        }

        appendCompactList(md, "\n## ✅ Успешно пройденные (" + passed.size() + ")\n", passed, baseUrl, runId);
        appendCompactList(md, "\n## ➡️ Не запущенные / прочие (" + rest.size() + ")\n", rest, baseUrl, runId);

        md.append("\n---\n_Сгенерировано Report Web App. Ссылки на тесты ведут на страницы с шагами, ")
                .append("stacktrace, скриншотами и аттачами._\n");
        return md.toString();
    }

    /** Детальный .md по тесту: аннотации, ошибка, шаги с параметрами, содержимое аттачей. */
    @Transactional(readOnly = true)
    public String exportTest(String runId, String resultId, String baseUrl) {
        AllureTestPageView test = storageService.buildTestPage(runId, resultId).orElse(null);
        if (test == null) {
            return null;
        }
        String testUrl = baseUrl + "/report/" + runId + "/allure/" + resultId;

        StringBuilder md = new StringBuilder(16_384);
        md.append("# Тест: ").append(text(test.getName(), test.getFullName())).append("\n\n");
        md.append("- **Статус**: `").append(test.getStatusLabel()).append("`\n");
        if (notBlank(test.getFullName())) {
            md.append("- **Полное имя**: `").append(test.getFullName()).append("`\n");
        }
        if (notBlank(test.getSuiteName())) {
            md.append("- **Сюита**: ").append(test.getSuiteName()).append("\n");
        }
        if (notBlank(test.getOwner())) {
            md.append("- **Владелец**: ").append(test.getOwner()).append("\n");
        }
        if (notBlank(test.getSeverity())) {
            md.append("- **Severity**: ").append(test.getSeverity()).append("\n");
        }
        StringBuilder section = new StringBuilder();
        if (notBlank(test.getEpic())) {
            section.append(test.getEpic());
        }
        if (notBlank(test.getFeature())) {
            section.append(section.length() > 0 ? " › " : "").append(test.getFeature());
        }
        if (notBlank(test.getStory())) {
            section.append(section.length() > 0 ? " › " : "").append(test.getStory());
        }
        if (section.length() > 0) {
            md.append("- **Раздел**: ").append(section).append("\n");
        }
        if (!test.getTags().isEmpty()) {
            md.append("- **Теги**: ").append(String.join(", ", test.getTags())).append("\n");
        }
        if (test.isFlaky()) {
            md.append("- **Flaky**: да\n");
        }
        if (notBlank(test.getDescription())) {
            md.append("- **Описание**: ").append(quote(test.getDescription())).append("\n");
        }
        md.append("- **Прогон**: [").append(text(test.getRunTitle(), "отчёт")).append("](")
                .append(baseUrl).append("/report/").append(runId).append(") (`").append(runId).append("`)\n");
        if (!test.getLinks().isEmpty()) {
            md.append("- **Ссылки**:\n");
            test.getLinks().forEach(l -> md.append("  - [").append(text(l.name(), l.url())).append("](")
                    .append(l.url()).append(") — ").append(l.typeLabel()).append("\n"));
        }

        if (notBlank(test.getStatusMessage()) || notBlank(test.getStatusTrace())) {
            md.append("\n## Ошибка падения\n\n");
            if (notBlank(test.getStatusMessage())) {
                md.append("**Message**\n\n```\n").append(cap(test.getStatusMessage(), MESSAGE_CAP)).append("\n```\n\n");
            }
            if (notBlank(test.getStatusTrace())) {
                md.append("**Stacktrace**\n\n```\n").append(cap(test.getStatusTrace(), TRACE_CAP)).append("\n```\n\n");
            }
        }

        if (!test.getParameters().isEmpty()) {
            md.append("\n## Параметры теста\n\n");
            test.getParameters().forEach(p -> md.append("- `").append(text(p.name(), "param"))
                    .append("` = `").append(cap(sanitize(p.value()), PARAM_CAP)).append("`\n"));
        }

        if (!test.getStepRows().isEmpty()) {
            md.append("\n## Шаги\n\n");
            for (StepRow row : test.getStepRows()) {
                md.append("  ".repeat(row.depth())).append("- ").append(row.step().status() != null
                        ? row.step().status() : "unknown").append(": ").append(text(row.step().name(), "шаг"));
                if (row.step().durationMs() > 0) {
                    md.append(" (").append(row.step().durationMs()).append(" мс)");
                }
                md.append("\n");
                if (!row.step().parameters().isEmpty()) {
                    StringBuilder params = new StringBuilder();
                    row.step().parameters().forEach(p -> {
                        if (params.length() > 0) {
                            params.append(", ");
                        }
                        params.append("`").append(text(p.name(), "param")).append("`=`")
                                .append(cap(sanitize(p.value()), PARAM_CAP)).append("`");
                    });
                    md.append("  ".repeat(row.depth() + 1)).append("  параметры: ").append(params).append("\n");
                }
                if (notBlank(row.step().message())) {
                    md.append("  ".repeat(row.depth() + 1)).append("  ошибка: ")
                            .append(quote(cap(row.step().message(), MESSAGE_CAP / 2))).append("\n");
                }
                row.step().attachments().forEach(a -> md.append("  ".repeat(row.depth() + 1))
                        .append("  аттач: [").append(text(a.name(), a.source())).append("](")
                        .append(baseUrl).append("/api/allure/attachments/").append(a.source()).append(")\n"));
            }
        }

        if (!test.getAttachments().isEmpty()) {
            md.append("\n## Вложения (attachments)\n\n");
            for (AllureTestPageView.AttachmentView a : test.getAttachments()) {
                md.append("### ").append(a.getName()).append("\n\n");
                md.append("- **Ссылка**: ").append(baseUrl).append(a.getUrl()).append("\n");
                md.append("- **Тип**: ").append(text(a.getContentType(), "неизвестен"))
                        .append(", **Размер**: ").append(a.getSizeBytes()).append(" байт\n\n");
                if (a.getInlineText() != null && !a.getInlineText().isBlank()) {
                    md.append("```text\n").append(cap(a.getInlineText(), ATTACHMENT_TEXT_CAP)).append("\n```\n\n");
                }
            }
        }

        md.append("---\n_Сгенерировано Report Web App из allure-results. Онлайн: ")
                .append(testUrl).append("_\n");
        return md.toString();
    }

    private void appendCompactList(StringBuilder md, String header, List<AllureTestResultEntity> items,
                                   String baseUrl, String runId) {
        md.append(header);
        for (AllureTestResultEntity r : items) {
            md.append("- ").append(icon(r)).append(" [").append(text(r.getName(), r.getFullName()))
                    .append("](").append(testUrl(baseUrl, runId, r.getId())).append(")");
            if (notBlank(r.getFullName())) {
                md.append(" — `").append(r.getFullName()).append("`");
            }
            md.append("\n");
        }
        if (items.isEmpty()) {
            md.append("_Нет._\n");
        }
    }

    private String categoryLabel(TestRunEntity run) {
        return categoryService.resolveByCode(run.getCategoryCode())
                .map(c -> c.icon() + " " + c.label())
                .orElseGet(() -> String.valueOf(run.getCategoryCode()));
    }

    private static String testUrl(String baseUrl, String runId, String resultId) {
        return baseUrl + "/report/" + runId + "/allure/" + resultId;
    }

    private static String icon(AllureTestResultEntity r) {
        return switch (r.getStatus() == null ? "unknown" : r.getStatus()) {
            case "passed" -> "✅";
            case "failed" -> "❌";
            case "broken" -> "⚠️";
            default -> "➡️";
        };
    }

    private static String text(String primary, String fallback) {
        return notBlank(primary) ? primary : notBlank(fallback) ? fallback : "(без имени)";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static int nz(Integer v) {
        return v != null ? v : 0;
    }

    private static String cap(String s, int limit) {
        if (s == null) {
            return "";
        }
        return s.length() <= limit ? s : s.substring(0, limit) + "… (обрезано, всего " + s.length() + " символов)";
    }

    /** В одну строку для inline-контекста. */
    private static String sanitize(String s) {
        return s == null ? "" : s.replace("\r", "").replace("\n", " ⏎ ");
    }

    /** Многострочное значение как markdown-цитата. */
    private static String quote(String s) {
        if (s == null) {
            return "";
        }
        return "\n  > " + s.replace("\r", "").replace("\n", "\n  > ") + "\n";
    }
}
