package com.appscreener.report.controller;

import com.appscreener.report.model.AllureTestResultDetails;
import com.appscreener.report.service.AllureTestOpsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

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
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            HttpStatus status = allureTestOpsService.isConfigured()
                    ? HttpStatus.BAD_GATEWAY
                    : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(err);
        }
    }
}
