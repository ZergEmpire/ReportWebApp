package com.appscreener.report.controller;

import com.appscreener.report.service.AllureMarkdownExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

/**
 * Выгрузка allure-данных в Markdown: сводный отчёт прогона и детальный отчёт по тесту
 * (для дальнейшей обработки через ИИ). Ссылки в выгрузке — абсолютные.
 */
@RestController
public class AllureExportController {

    private final AllureMarkdownExportService exportService;

    public AllureExportController(AllureMarkdownExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/report/{runId}/export.md")
    public ResponseEntity<String> exportRun(@PathVariable String runId, UriComponentsBuilder uriBuilder) {
        String md = exportService.exportRun(runId, baseUrl(uriBuilder));
        if (md == null) {
            return ResponseEntity.notFound().build();
        }
        return markdown(md, "run-" + runId + ".md");
    }

    @GetMapping("/report/{runId}/allure/{resultId}/export.md")
    public ResponseEntity<String> exportTest(@PathVariable String runId, @PathVariable String resultId,
                                             UriComponentsBuilder uriBuilder) {
        String md = exportService.exportTest(runId, resultId, baseUrl(uriBuilder));
        if (md == null) {
            return ResponseEntity.notFound().build();
        }
        return markdown(md, "test-" + resultId + ".md");
    }

    private static String baseUrl(UriComponentsBuilder uriBuilder) {
        return uriBuilder.build().toUriString();
    }

    private static ResponseEntity<String> markdown(String md, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "markdown", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(md, headers, HttpStatus.OK);
    }
}
