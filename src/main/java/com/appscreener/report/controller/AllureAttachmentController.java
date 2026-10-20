package com.appscreener.report.controller;

import com.appscreener.report.entity.AllureAttachmentEntity;
import com.appscreener.report.service.AllureResultsStorageService;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Раздача содержимого allure-аттачей (скриншоты, тексты, html, видео) стримингом из
 * пака прогона — файл не грузится в память целиком.
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

        AllureResultsStorageService.AttachmentStream handle;
        try {
            var handleOpt = storage.openAttachmentStream(meta);
            if (handleOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("error", "Файл аттача не найден в паке прогона: " + meta.getSource()));
            }
            handle = handleOpt.get();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "Не удалось открыть пак аттачей: " + e.getMessage()));
        }

        String contentType = meta.getContentType() != null ? meta.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.inline().filename(safeFileName(meta.getSource())).build());
        return ResponseEntity.ok().headers(headers)
                .body(new PackedAttachmentResource(handle, safeFileName(meta.getSource())));
    }

    /** Ресурс со стримом из pak-файла: размер известен из zip-заголовков; закрытие потока закрывает pak. */
    private static final class PackedAttachmentResource extends AbstractResource {

        private final AllureResultsStorageService.AttachmentStream handle;
        private final String filename;

        private PackedAttachmentResource(AllureResultsStorageService.AttachmentStream handle, String filename) {
            this.handle = handle;
            this.filename = filename;
        }

        @Override
        public String getDescription() {
            return "allure attachment " + filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public long contentLength() {
            return handle.size();
        }

        @Override
        public InputStream getInputStream() {
            return new FilterInputStream(handle.stream()) {
                @Override
                public void close() throws IOException {
                    handle.close();
                }
            };
        }
    }

    private static String safeFileName(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "attachment" : cleaned;
    }
}
