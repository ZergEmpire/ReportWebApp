package com.appscreener.report.controller;

import com.appscreener.report.model.ReportCategory;
import com.appscreener.report.model.ReportType;
import com.appscreener.report.model.TestRunDetailView;
import com.appscreener.report.service.AllureTestOpsService;
import com.appscreener.report.service.ReportStorageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
public class DashboardController {

    private final ReportStorageService storage;
    private final AllureTestOpsService allureTestOpsService;

    public DashboardController(ReportStorageService storage, AllureTestOpsService allureTestOpsService) {
        this.storage = storage;
        this.allureTestOpsService = allureTestOpsService;
    }

    @GetMapping("/")
    public String dashboard(
            @RequestParam(value = "category", required = false) String category,
            Model model) {

        String categoryCode = category != null ? category : "all";
        List<TestRunDetailView> runs = storage.findRuns(categoryCode);
        List<TestRunDetailView> summaryRuns = runs.stream()
                .filter(r -> r.getReportType() == ReportType.TEST_RUN_SUMMARY
                        || r.getReportType() == ReportType.DAILY_SUMMARY)
                .toList();
        ReportStorageService.DashboardStats stats = storage.computeStats(categoryCode);

        model.addAttribute("runs", runs);
        model.addAttribute("summaryRuns", summaryRuns);
        model.addAttribute("stats", stats);
        model.addAttribute("categories", ReportCategory.navigable());
        model.addAttribute("selectedCategory", categoryCode);
        return "dashboard";
    }

    @GetMapping("/report/{id}")
    public String reportDetail(@PathVariable("id") String id, Model model) {
        Optional<TestRunDetailView> run = storage.findRunDetail(id);
        if (run.isEmpty()) {
            return "redirect:/";
        }
        model.addAttribute("run", run.get());
        model.addAttribute("allureConfigured", allureTestOpsService.isConfigured());
        return "report-detail";
    }
}
