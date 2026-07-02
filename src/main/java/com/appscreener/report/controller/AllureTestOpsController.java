package com.appscreener.report.controller;

import com.appscreener.report.model.AllureAttachmentMeta;
import com.appscreener.report.model.AllureTestResultDetails;
import com.appscreener.report.service.AllureTestOpsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/allure")
public class AllureTestOpsController {

    private final AllureTestOpsService allureTestOpsService;

    public AllureTestOpsController(AllureTestOpsService allureTestOpsService) {
        this.allureTestOpsService = allureTestOpsService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configured", allureTestOpsService.isConfigured());
        return body;
    }

    @GetMapping("/testresult/{testResultId}")
    public ResponseEntity<?> testResult(@PathVariable long testResultId) {
        try {
            AllureTestResultDetails details = allureTestOpsService.fetchTestResult(testResultId);
            return ResponseEntity.ok(details);
        } catch (IllegalStateException e) {
            return allureError(e);
        }
    }

    /**
     * Скриншот падения из вложений test result (проксирует Allure TestOps).
     */
    @GetMapping("/testresult/{testResultId}/screenshot")
    public ResponseEntity<?> screenshot(@PathVariable long testResultId) {
        try {
            Optional<AllureAttachmentMeta> attachment = allureTestOpsService.findScreenshotAttachment(testResultId);
            if (attachment.isEmpty()) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "Скриншот не найден среди вложений test result " + testResultId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
            }
            AllureAttachmentMeta meta = attachment.get();
            byte[] content = allureTestOpsService.fetchAttachmentContent(meta.getId());
            String contentType = meta.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = MediaType.IMAGE_PNG_VALUE;
            }
            String fileName = safeFileName(meta.getName(), testResultId, "screenshot.png");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(content);
        } catch (IllegalStateException e) {
            return allureError(e);
        }
    }

    /**
     * Основной attachment test result (например, HTML-таблица с метриками).
     */
    @GetMapping("/testresult/{testResultId}/attachment")
    public ResponseEntity<?> attachment(@PathVariable long testResultId) {
        try {
            Optional<AllureAttachmentMeta> attachment = allureTestOpsService.findMainAttachment(testResultId);
            if (attachment.isEmpty()) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "Аттачменты отсутствуют в Allure TestOps для test result " + testResultId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
            }
            AllureAttachmentMeta meta = attachment.get();
            byte[] content = allureTestOpsService.fetchAttachmentContent(meta.getId());
            String contentType = meta.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = MediaType.TEXT_PLAIN_VALUE;
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + safeFileName(meta.getName(), testResultId, "attachment.txt") + "\"")
                    .header("X-Allure-Attachment-Name", safeHeaderValue(meta.getName()))
                    .body(content);
        } catch (IllegalStateException e) {
            return allureError(e);
        }
    }

    private ResponseEntity<Map<String, Object>> allureError(IllegalStateException e) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", e.getMessage());
        HttpStatus status = allureTestOpsService.isConfigured()
                ? HttpStatus.BAD_GATEWAY
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(err);
    }

    private static String safeFileName(String name, long testResultId, String fallback) {
        if (name == null || name.isBlank()) {
            return fallbackPrefix(fallback, testResultId);
        }
        String cleaned = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? fallbackPrefix(fallback, testResultId) : cleaned;
    }

    private static String fallbackPrefix(String fallback, long testResultId) {
        int dot = fallback.lastIndexOf('.');
        if (dot <= 0) {
            return fallback + "-" + testResultId;
        }
        return fallback.substring(0, dot) + "-" + testResultId + fallback.substring(dot);
    }

    private static String safeHeaderValue(String value) {
        if (value == null || value.isBlank()) {
            return "attachment";
        }
        return value.replaceAll("[\\r\\n\"]", "_");
    }
}
