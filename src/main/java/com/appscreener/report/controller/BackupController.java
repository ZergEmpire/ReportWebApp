package com.appscreener.report.controller;

import com.appscreener.report.backup.BackupOutcome;
import com.appscreener.report.service.BackupService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping
    public Map<String, Object> status() throws Exception {
        return Map.of(
                "enabled", backupService.isEnabled(),
                "directory", backupService.backupDirectory().toString(),
                "backups", backupService.listBackups()
        );
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create() throws Exception {
        if (!backupService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Backup disabled");
        }
        BackupOutcome outcome = backupService.createBackup(true);
        if (outcome.isSkipped()) {
            return ResponseEntity.ok(Map.of("skipped", true, "message", outcome.getMessage()));
        }
        return ResponseEntity.ok(Map.of("skipped", false, "backup", outcome.getBackup()));
    }

    @GetMapping("/{fileName}/download")
    public ResponseEntity<Resource> download(@PathVariable("fileName") String fileName) {
        Path path = backupService.resolveBackupFile(fileName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "restore", defaultValue = "false") boolean restore) throws Exception {
        if (!backupService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Backup disabled");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл не выбран");
        }
        var info = backupService.uploadArchive(file.getInputStream(), file.getOriginalFilename(), restore);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "restored", restore,
                "backup", info,
                "message", restore
                        ? "Архив загружен и восстановлен: " + info.getFileName()
                        : "Архив загружен: " + info.getFileName()
        ));
    }

    @PostMapping("/restore/{fileName}")
    public ResponseEntity<Map<String, String>> restore(@PathVariable("fileName") String fileName) throws Exception {
        backupService.restoreBackup(fileName);
        return ResponseEntity.ok(Map.of(
                "ok", "true",
                "message", "История восстановлена из " + fileName + ". Обновите страницу Dashboard."
        ));
    }
}
