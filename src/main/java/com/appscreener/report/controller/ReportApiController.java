package com.appscreener.report.controller;

import com.appscreener.report.service.ReportStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ReportApiController {

    private final ReportStorageService storage;

    public ReportApiController(ReportStorageService storage) {
        this.storage = storage;
    }

    @RequestMapping(
            value = "/sendMessage",
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = {
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                    MediaType.MULTIPART_FORM_DATA_VALUE,
                    MediaType.ALL_VALUE
            }
    )
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestParam(value = "text", required = false) String text,
            @RequestPart(value = "text", required = false) MultipartFile textPart,
            @RequestParam(value = "parse_mode", required = false, defaultValue = "Markdown") String parseMode,
            @RequestParam(value = "message_thread_id", required = false) String messageThreadId,
            @RequestParam(value = "run_id", required = false) String runId,
            @RequestParam(value = "chat_id", required = false) String chatId) throws Exception {

        String messageText = resolveText(text, textPart);
        String messageId = storage.save(messageText, parseMode, messageThreadId, runId);
        return ResponseEntity.ok(telegramOkResponse(messageId));
    }

    @RequestMapping(
            value = "/pinChatMessage",
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> pinChatMessage(
            @RequestParam("message_id") String messageId,
            @RequestParam(value = "chat_id", required = false) String chatId) {

        boolean pinned = storage.pin(messageId);
        if (!pinned) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("description", "Message not found: " + messageId);
            return ResponseEntity.badRequest().body(err);
        }
        return ResponseEntity.ok(telegramOkResponse(messageId));
    }

    private String resolveText(String text, MultipartFile textPart) throws Exception {
        if (text != null && !text.isBlank()) {
            return text;
        }
        if (textPart != null && !textPart.isEmpty()) {
            return new String(textPart.getBytes(), StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Parameter 'text' is required");
    }

    private Map<String, Object> telegramOkResponse(String messageId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message_id", messageId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("result", result);
        return body;
    }
}
