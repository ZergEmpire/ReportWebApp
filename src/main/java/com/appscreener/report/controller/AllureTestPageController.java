package com.appscreener.report.controller;

import com.appscreener.report.model.allure.AllureTestPageView;
import com.appscreener.report.service.AllureResultsStorageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Страница теста allure-прогона (шаги, stacktrace, скриншоты, аттачи).
 */
@Controller
public class AllureTestPageController {

    private final AllureResultsStorageService storage;

    public AllureTestPageController(AllureResultsStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/report/{runId}/allure/{resultId}")
    public String testPage(@PathVariable String runId, @PathVariable String resultId, Model model) {
        var view = storage.buildTestPage(runId, resultId);
        if (view.isEmpty()) {
            return "redirect:/report/" + runId;
        }
        model.addAttribute("test", view.get());
        return "allure-test-detail";
    }
}
