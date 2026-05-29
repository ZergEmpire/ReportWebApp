package com.appscreener.report.service;

import com.appscreener.report.entity.ReportMessageEntity;
import com.appscreener.report.entity.TestRunEntity;
import com.appscreener.report.model.ParsedReport;
import com.appscreener.report.model.CategoryInfo;
import com.appscreener.report.model.ReportCategory;
import com.appscreener.report.model.ReportType;
import com.appscreener.report.model.TestLineItem;
import com.appscreener.report.model.TestRunDetailView;
import com.appscreener.report.parser.MarkdownReportParser;
import com.appscreener.report.repository.ReportMessageRepository;
import com.appscreener.report.repository.TestRunRepository;
import com.appscreener.report.util.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReportStorageService {

    private static final int RUN_LINK_MINUTES = 30;

    private final TestRunRepository testRunRepository;
    private final ReportMessageRepository messageRepository;
    private final MarkdownReportParser parser;
    private final AllureTestOpsService allureTestOpsService;
    private final CategoryService categoryService;

    public ReportStorageService(TestRunRepository testRunRepository,
                                ReportMessageRepository messageRepository,
                                MarkdownReportParser parser,
                                AllureTestOpsService allureTestOpsService,
                                CategoryService categoryService) {
        this.testRunRepository = testRunRepository;
        this.messageRepository = messageRepository;
        this.parser = parser;
        this.allureTestOpsService = allureTestOpsService;
        this.categoryService = categoryService;
    }

    @Transactional
    public String save(String text, String parseMode, String messageThreadId, String runId) {
        CategoryInfo category = categoryService.resolveByThreadId(messageThreadId)
                .filter(c -> !c.isAll())
                .orElse(CategoryInfo.fromEnum(ReportCategory.GENERAL));

        ParsedReport parsed = parser.parse(text, category.code());
        String messageId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant now = Instant.now();

        TestRunEntity run = resolveTestRun(parsed, category, runId, messageId, now);
        applySummaryFields(run, parsed);

        ReportMessageEntity message = new ReportMessageEntity();
        message.setId(messageId);
        message.setTestRun(run);
        message.setReceivedAt(now);
        message.setReportType(parsed.getReportType());
        message.setRawText(text);
        if (!parsed.getTestItems().isEmpty()) {
            message.setTestItemsJson(JsonUtil.toJson(parsed.getTestItems()));
        }
        messageRepository.save(message);

        if (returnsRunId(parsed.getReportType())) {
            return run.getId();
        }
        return messageId;
    }

    @Transactional
    public boolean pin(String messageId) {
        Optional<TestRunEntity> runOpt = testRunRepository.findById(messageId);
        if (runOpt.isEmpty()) {
            return false;
        }
        testRunRepository.unpinAllSummaries();
        TestRunEntity run = runOpt.get();
        run.setPinned(true);
        testRunRepository.save(run);
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<TestRunDetailView> findRunDetail(String runId) {
        return testRunRepository.findById(runId).map(this::buildDetailView);
    }

    @Transactional(readOnly = true)
    public List<TestRunDetailView> findRuns(String categoryCode) {
        List<TestRunEntity> runs;
        if (categoryCode == null || categoryCode.isBlank() || "all".equalsIgnoreCase(categoryCode)) {
            runs = testRunRepository.findByCategoryCodeNotOrderByReceivedAtDesc(
                    ReportCategory.DEBUG.getCode());
        } else {
            runs = testRunRepository.findByCategoryCodeOrderByReceivedAtDesc(categoryCode);
        }
        return runs.stream().map(this::buildSummaryCard).toList();
    }

    @Transactional(readOnly = true)
    public DashboardStats computeStats(String categoryCode) {
        List<TestRunDetailView> runs = findRuns(categoryCode).stream()
                .filter(r -> r.getReportType() == ReportType.TEST_RUN_SUMMARY
                        || r.getReportType() == ReportType.DAILY_SUMMARY)
                .toList();

        int total = runs.size();
        long success = runs.stream().filter(TestRunDetailView::isSuccess).count();
        int passedSum = runs.stream()
                .map(TestRunDetailView::getPassedTests)
                .filter(v -> v != null)
                .mapToInt(Integer::intValue)
                .sum();
        int failedSum = runs.stream()
                .map(TestRunDetailView::getFailedTests)
                .filter(v -> v != null)
                .mapToInt(Integer::intValue)
                .sum();

        return new DashboardStats(total, success, total - success, passedSum, failedSum);
    }

    @Transactional
    public int deleteOlderThan(Instant cutoff) {
        messageRepository.deleteOlderThan(cutoff);
        return testRunRepository.deleteOlderThan(cutoff);
    }

    private TestRunEntity resolveTestRun(ParsedReport parsed, CategoryInfo category,
                                         String runId, String messageId, Instant now) {
        if (runId != null && !runId.isBlank()) {
            Optional<TestRunEntity> existing = testRunRepository.findById(runId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (returnsRunId(parsed.getReportType())) {
            TestRunEntity run = new TestRunEntity();
            run.setId(messageId);
            run.setCategoryCode(category.code());
            run.setReceivedAt(now);
            run.setReportType(parsed.getReportType());
            run.setTitle(parsed.getTitle());
            return testRunRepository.save(run);
        }

        Instant windowStart = now.minus(RUN_LINK_MINUTES, ChronoUnit.MINUTES);
        return testRunRepository
                .findFirstByCategoryCodeAndReceivedAtAfterOrderByReceivedAtDesc(category.code(), windowStart)
                .orElseGet(() -> {
                    TestRunEntity orphan = new TestRunEntity();
                    orphan.setId(messageId);
                    orphan.setCategoryCode(category.code());
                    orphan.setReceivedAt(now);
                    orphan.setReportType(ReportType.UNKNOWN);
                    orphan.setTitle("Прогон (без сводки)");
                    return testRunRepository.save(orphan);
                });
    }

    private void applySummaryFields(TestRunEntity run, ParsedReport parsed) {
        if (!returnsRunId(parsed.getReportType()) && parsed.getReportType() != ReportType.UNKNOWN) {
            return;
        }
        // Служебные сообщения (ссылка на Report Web App и т.п.) — UNKNOWN без полей сводки; не затираем run
        if (parsed.getReportType() == ReportType.UNKNOWN && !hasSummaryPayload(parsed)) {
            return;
        }
        if (parsed.getReportType() != ReportType.UNKNOWN) {
            run.setReportType(parsed.getReportType());
            if (parsed.getTitle() != null) {
                run.setTitle(parsed.getTitle());
            }
        }
        if (parsed.getStandUrl() != null && !parsed.getStandUrl().isBlank()) {
            run.setStandUrl(parsed.getStandUrl().trim());
        }
        if (!parsed.getSuiteNames().isEmpty()) {
            run.setSuiteNamesJson(JsonUtil.toJson(parsed.getSuiteNames()));
        }
        if (parsed.getCiSuites() != null) {
            run.setCiSuites(parsed.getCiSuites());
        }
        if (parsed.getTotalTests() != null) {
            run.setTotalTests(parsed.getTotalTests());
        }
        if (parsed.getPassedTests() != null) {
            run.setPassedTests(parsed.getPassedTests());
        }
        if (parsed.getFailedTests() != null) {
            run.setFailedTests(parsed.getFailedTests());
        }
        if (parsed.getSkippedTests() != null) {
            run.setSkippedTests(parsed.getSkippedTests());
        }
        if (parsed.getExecutionTime() != null) {
            run.setExecutionTime(parsed.getExecutionTime());
        }
        if (parsed.getPipelineUrl() != null) {
            run.setPipelineUrl(parsed.getPipelineUrl());
        }
        if (parsed.getAllureUrl() != null) {
            run.setAllureUrl(parsed.getAllureUrl());
        }
        if (parsed.getSummaryDate() != null) {
            run.setSummaryDate(parsed.getSummaryDate());
        }
        testRunRepository.save(run);
    }

    private static boolean hasSummaryPayload(ParsedReport parsed) {
        return (parsed.getStandUrl() != null && !parsed.getStandUrl().isBlank())
                || !parsed.getSuiteNames().isEmpty()
                || (parsed.getCiSuites() != null && !parsed.getCiSuites().isBlank())
                || parsed.getTotalTests() != null
                || parsed.getPassedTests() != null
                || parsed.getFailedTests() != null
                || parsed.getSkippedTests() != null
                || parsed.getExecutionTime() != null
                || parsed.getPipelineUrl() != null
                || parsed.getAllureUrl() != null
                || parsed.getSummaryDate() != null;
    }

    private TestRunDetailView buildDetailView(TestRunEntity run) {
        TestRunDetailView view = buildSummaryCard(run);
        List<ReportMessageEntity> messages = messageRepository.findByTestRun_IdOrderByReceivedAtAsc(run.getId());

        for (ReportMessageEntity msg : messages) {
            List<TestLineItem> items = JsonUtil.testItemsFromJson(msg.getTestItemsJson());
            if (items.isEmpty() && msg.getRawText() != null && !msg.getRawText().isBlank()) {
                ParsedReport reparsed = parser.parse(msg.getRawText(), run.getCategoryCode());
                items = reparsed.getTestItems();
            }
            enrichAllureTestResultIds(items);
            ReportType type = msg.getReportType();
            if (type == ReportType.UNKNOWN && !items.isEmpty()) {
                type = reparsedTypeFromItems(items, msg.getRawText());
            }
            switch (type) {
                case FAILED_TESTS -> view.getFailedTestsList().addAll(items);
                case PASSED_TESTS -> view.getPassedTestsList().addAll(items);
                case SKIPPED_TESTS -> view.getSkippedTestsList().addAll(items);
                case SUCCESSFUL_SUITES -> {
                    view.getSuccessfulSuites().addAll(items);
                    view.getSuiteResults().addAll(items);
                }
                case FAILED_SUITES -> {
                    view.getFailedSuites().addAll(items);
                    view.getSuiteResults().addAll(items);
                }
                case NOTIFICATION -> view.getNotificationItems().addAll(items);
                default -> {
                }
            }
        }
        return view;
    }

    private TestRunDetailView buildSummaryCard(TestRunEntity run) {
        TestRunDetailView view = new TestRunDetailView();
        view.setId(run.getId());
        view.setCategoryCode(run.getCategoryCode());
        CategoryInfo cat = categoryService.resolveByCode(run.getCategoryCode())
                .filter(c -> !c.isAll())
                .orElse(CategoryInfo.fromEnum(ReportCategory.GENERAL));
        view.setCategoryLabel(cat.label());
        view.setCategoryIcon(cat.icon());
        view.setReceivedAt(run.getReceivedAt());
        view.setPinned(run.isPinned());
        view.setReportType(run.getReportType());
        view.setTitle(run.getTitle());
        view.setStandUrl(run.getStandUrl());
        view.setSuiteNames(JsonUtil.stringListFromJson(run.getSuiteNamesJson()));
        view.setCiSuites(run.getCiSuites());
        view.setTotalTests(run.getTotalTests());
        view.setPassedTests(run.getPassedTests());
        view.setFailedTests(run.getFailedTests());
        view.setSkippedTests(run.getSkippedTests());
        view.setExecutionTime(run.getExecutionTime());
        view.setPipelineUrl(run.getPipelineUrl());
        view.setAllureUrl(run.getAllureUrl());
        view.setSummaryDate(run.getSummaryDate());
        return view;
    }

    private void enrichAllureTestResultIds(List<TestLineItem> items) {
        for (TestLineItem item : items) {
            if (item.getAllureTestResultId() == null && item.getUrl() != null) {
                allureTestOpsService.parseTestResultId(item.getUrl()).ifPresent(item::setAllureTestResultId);
            }
        }
    }

    private ReportType reparsedTypeFromItems(List<TestLineItem> items, String rawText) {
        if (rawText != null) {
            if (rawText.contains("Упавшие тесты")) {
                return ReportType.FAILED_TESTS;
            }
            if (rawText.contains("Успешно пройденные тесты")) {
                return ReportType.PASSED_TESTS;
            }
            if (rawText.contains("не были запущены")) {
                return ReportType.SKIPPED_TESTS;
            }
        }
        long failed = items.stream().filter(i -> "❌".equals(i.getIcon())).count();
        long passed = items.stream().filter(i -> "✅".equals(i.getIcon())).count();
        long skipped = items.stream().filter(i -> "➡️".equals(i.getIcon())).count();
        if (failed > 0 && passed == 0 && skipped == 0) {
            return ReportType.FAILED_TESTS;
        }
        if (passed > 0 && failed == 0 && skipped == 0) {
            return ReportType.PASSED_TESTS;
        }
        if (skipped > 0 && failed == 0 && passed == 0) {
            return ReportType.SKIPPED_TESTS;
        }
        return ReportType.UNKNOWN;
    }

    private boolean returnsRunId(ReportType type) {
        return type == ReportType.TEST_RUN_SUMMARY
                || type == ReportType.DAILY_SUMMARY
                || type == ReportType.NOTIFICATION;
    }

    public record DashboardStats(int totalReports, long successfulRuns, long failedRuns,
                               int totalPassed, int totalFailed) {
    }
}
