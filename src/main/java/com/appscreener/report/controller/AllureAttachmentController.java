package com.appscreener.report.controller;

import com.appscreener.report.entity.AllureAttachmentEntity;
import com.appscreener.report.service.AllureResultsStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Раздача содержимого allure-аттачей (скриншоты, тексты, html), хранящихся на диске сервиса.
 */
@RestController
@RequestMapping("/api/allure")
public class AllureAttachmentController {

    private final AllureResultsStorageService storage;

    public AllureAttachmentController(AllureResultsStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/attachments/{source}")
    public ResponseEntity<?> attachment(@PathVariable String source) {
        var attachment = storage.findAttachment(source);
        if (attachment.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AllureAttachmentEntity meta = attachment.get();
        try {
            byte[] content = storage.readAttachmentContent(meta);
            String contentType = meta.getContentType() != null ? meta.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFileName(meta.getSource()) + "\"")
                    .body(content);
        } catch (java.io.IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "Файл аттача не найден: " + meta.getSource()));
        }
    }

    private static String safeFileName(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "attachment" : cleaned;
    }
}
