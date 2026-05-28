package com.appscreener.report.controller;

import com.appscreener.report.config.BackupProperties;
import com.appscreener.report.service.BackupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BackupPageController {

    private final BackupService backupService;
    private final BackupProperties properties;

    public BackupPageController(BackupService backupService, BackupProperties properties) {
        this.backupService = backupService;
        this.properties = properties;
    }

    @GetMapping("/backup")
    public String page(Model model) throws Exception {
        model.addAttribute("enabled", properties.isEnabled());
        model.addAttribute("directory", backupService.backupDirectory());
        model.addAttribute("keepCount", properties.getKeepCount());
        model.addAttribute("cron", properties.getCron());
        model.addAttribute("skipIfUnchanged", properties.isSkipIfUnchanged());
        model.addAttribute("backups", backupService.listBackups());
        return "backup";
    }

    @PostMapping("/backup/create")
    public String create(RedirectAttributes flash) {
        try {
            var outcome = backupService.createBackup(true);
            if (outcome.isSkipped()) {
                flash.addFlashAttribute("message", outcome.getMessage());
                flash.addFlashAttribute("messageType", "warn");
            } else {
                var info = outcome.getBackup();
                flash.addFlashAttribute("message",
                        "Архив: " + info.getFileName() + " (" + info.getRunCount() + " прогонов, "
                                + info.getMessageCount() + " сообщений)");
                flash.addFlashAttribute("messageType", "ok");
            }
        } catch (Exception e) {
            flash.addFlashAttribute("message", "Ошибка: " + e.getMessage());
            flash.addFlashAttribute("messageType", "error");
        }
        return "redirect:/backup";
    }

    @PostMapping("/backup/upload")
    public String upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "restore", defaultValue = "false") boolean restore,
            RedirectAttributes flash) {
        try {
            if (file == null || file.isEmpty()) {
                flash.addFlashAttribute("message", "Выберите файл архива (.json.gz или .zip)");
                flash.addFlashAttribute("messageType", "error");
                return "redirect:/backup";
            }
            var info = backupService.uploadArchive(file.getInputStream(), file.getOriginalFilename(), restore);
            String action = restore ? "загружен и восстановлен" : "загружен в каталог backups";
            flash.addFlashAttribute("message",
                    "Архив " + action + ": " + info.getFileName() + " ("
                            + info.getRunCount() + " прогонов, " + info.getMessageCount() + " сообщений)");
            flash.addFlashAttribute("messageType", restore ? "warn" : "ok");
        } catch (Exception e) {
            flash.addFlashAttribute("message", "Ошибка загрузки: " + e.getMessage());
            flash.addFlashAttribute("messageType", "error");
        }
        return "redirect:/backup";
    }

    @PostMapping("/backup/restore")
    public String restore(@RequestParam("fileName") String fileName, RedirectAttributes flash) {
        try {
            var stats = backupService.readArchiveStats(fileName);
            backupService.restoreBackup(fileName);
            flash.addFlashAttribute("message",
                    "Восстановлено из " + fileName + ": " + stats.runCount() + " прогонов, "
                            + stats.messageCount() + " сообщений. Откройте Dashboard.");
            flash.addFlashAttribute("messageType", "ok");
        } catch (Exception e) {
            flash.addFlashAttribute("message", "Ошибка восстановления: " + e.getMessage());
            flash.addFlashAttribute("messageType", "error");
        }
        return "redirect:/backup";
    }
}
