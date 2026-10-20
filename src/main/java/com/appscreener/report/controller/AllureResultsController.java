package com.appscreener.report.controller;

import com.appscreener.report.model.allure.AllureParsedResult;
import com.appscreener.report.service.AllureResultsStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Приём allure-results от TelegramTestListener: zip каталога allure-results + метаданные прогона.
 * Ответ — в стиле Telegram Bot API ({@code {ok, result:{message_id,…}}}), как у /sendMessage.
 */
@RestController
public class AllureResultsController {

    private final AllureResultsStorageService storage;

    public AllureResultsController(AllureResultsStorageService storage) {
        this.storage = storage;
    }

    @PostMapping(
            value = "/allure-results",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, Object>> upload(
            @RequestPart("archive") MultipartFile archive,
            @RequestParam(value = "message_thread_id", required = false) String messageThreadId,
            @RequestParam(value = "run_id", required = false) String runId,
            @RequestParam(value = "stand_url", required = false) String standUrl,
            @RequestParam(value = "ci_suites", required = false) String ciSuites,
            @RequestParam(value = "pipeline_url", required = false) String pipelineUrl,
            @RequestParam(value = "title", required = false) String title,
            UriComponentsBuilder uriBuilder) throws IOException {

        if (archive == null || archive.isEmpty()) {
            return badRequest("Параметр 'archive' (zip allure-results) обязателен");
        }

        AllureResultsStorageService.RunMeta meta = new AllureResultsStorageService.RunMeta(
                standUrl, ciSuites, pipelineUrl, title);

        AllureResultsStorageService.ImportOutcome outcome;
        try (var in = archive.getInputStream()) {
            outcome = storage.importZip(in, runId, messageThreadId, meta);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }

        String runIdAssigned = outcome.run().getId();
        String baseUrl = uriBuilder.build().toUriString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message_id", runIdAssigned);
        result.put("run_id", runIdAssigned);
        result.put("report_url", baseUrl + "/report/" + runIdAssigned);
        result.put("summary", summary(outcome));
        result.put("tests", tests(outcome, baseUrl));
        return ResponseEntity.ok(Map.of("ok", true, "result", result));
    }

    private static Map<String, Object> summary(AllureResultsStorageService.ImportOutcome outcome) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", outcome.parsed().results().size());
        summary.put("passed", outcome.parsed().passedCount());
        summary.put("failed", outcome.parsed().countByStatus("failed"));
        summary.put("broken", outcome.parsed().countByStatus("broken"));
        summary.put("skipped", outcome.parsed().skippedCount());
        summary.put("duration", outcome.run().getExecutionTime());
        summary.put("suites", outcome.parsed().suites());
        return summary;
    }

    private static List<Map<String, Object>> tests(AllureResultsStorageService.ImportOutcome outcome, String baseUrl) {
        List<Map<String, Object>> tests = new ArrayList<>();
        for (AllureParsedResult r : outcome.parsed().results()) {
            Map<String, Object> test = new LinkedHashMap<>();
            test.put("uuid", r.uuid());
            test.put("name", r.name() != null ? r.name() : r.fullName());
            test.put("full_name", r.fullName());
            test.put("status", r.status());
            test.put("url", baseUrl + "/report/" + outcome.run().getId() + "/allure/" + r.uuid());
            tests.add(test);
        }
        return tests;
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String description) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("ok", false);
        err.put("description", description);
        return ResponseEntity.badRequest().body(err);
    }
}
