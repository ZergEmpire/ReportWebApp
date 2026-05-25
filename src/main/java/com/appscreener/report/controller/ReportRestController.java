package com.appscreener.report.controller;

import com.appscreener.report.entity.ReportMessageEntity;
import com.appscreener.report.model.TestRunDetailView;
import com.appscreener.report.repository.ReportMessageRepository;
import com.appscreener.report.service.ReportStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReportRestController {

    private final ReportStorageService storage;
    private final ReportMessageRepository messageRepository;

    public ReportRestController(ReportStorageService storage, ReportMessageRepository messageRepository) {
        this.storage = storage;
        this.messageRepository = messageRepository;
    }

    @GetMapping("/runs")
    public List<TestRunDetailView> list(@RequestParam(value = "category", required = false) String category) {
        return storage.findRuns(category != null ? category : "all");
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<TestRunDetailView> get(@PathVariable("id") String id) {
        return storage.findRunDetail(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ReportStorageService.DashboardStats stats(
            @RequestParam(value = "category", required = false) String category) {
        return storage.computeStats(category != null ? category : "all");
    }

    @GetMapping("/runs/{id}/messages")
    public java.util.List<java.util.Map<String, String>> messages(@PathVariable("id") String id) {
        return messageRepository.findByTestRun_IdOrderByReceivedAtAsc(id).stream()
                .map(m -> java.util.Map.of(
                        "reportType", m.getReportType().name(),
                        "rawText", m.getRawText() != null ? m.getRawText() : ""
                ))
                .toList();
    }
}
