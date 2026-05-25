package com.appscreener.report.config;

import com.appscreener.report.service.ReportStorageService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Загрузка демо-отчётов: запуск с -Dreport.seed-demo=true
 */
@Component
@ConditionalOnProperty(name = "report.seed-demo", havingValue = "true")
public class DemoDataLoader implements ApplicationRunner {

    private final ReportStorageService storage;

    public DemoDataLoader(ReportStorageService storage) {
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        seedRun("2", "seed-payloads/api-summary.txt",
                failed(),
                "✅ [GET] Get projects list\n✅ [POST] Create scan\n✅ [DELETE] Delete project\n",
                skipped());
        seedRun("4", "seed-payloads/ui-summary.txt",
                "========================\n*Упавшие тесты с ошибкой: \n*\n========================\n"
                        + "❌ [Login] Wrong password\n❌ [Projects] Filter by rating\n❌ [Projects] Download report timeout\n"
                        + "❌ [Admin] Create user duplicate\n❌ [Scan] Start scan N/A\n",
                "========================\n*Успешно пройденные тесты: \n*\n========================\n"
                        + "✅ [Home] Open dashboard\n✅ [Projects] Watch project page\n",
                "========================\n*Тесты, которые не были запущены из-за системной ошибки: \n*\n========================\n"
                        + "➡️ [DAST] Stand unavailable\n➡️ [OSA] License expired\n");
        seedRun("2154", "seed-payloads/express-summary.txt",
                "========================\n*Упавшие тесты с ошибкой: \n*\n========================\n"
                        + "❌ [DelphiFull] CustomMenu_main_with_dep.zip\n❌ [Python] sample_project.zip\n"
                        + "❌ [Go] module_vulnerable.zip\n❌ [Ruby] rails_legacy.zip\n",
                "========================\n*Успешно пройденные тесты: \n*\n========================\n"
                        + "✅ [C++] cmake_project.zip\n✅ [C#] dotnet8_app.zip\n✅ [Erlang] otp_release.zip\n",
                skipped());
        seedRun("2152", "seed-payloads/healthy-summary.txt",
                "========================\n*Упавшие тесты с ошибкой: \n*\n========================\n"
                        + "❌ [Health] DB connection pool exhausted\n",
                "========================\n*Успешно пройденные тесты: \n*\n========================\n"
                        + "✅ [Health] API ping\n✅ [Health] Disk space check\n",
                skipped());
        seedRun("2913", "seed-payloads/release-summary.txt",
                "========================\n*Упавшие тесты с ошибкой: \n*\n========================\n"
                        + "❌ [Release] SAST analyse regression\n❌ [Release] OSA full scan failed\n"
                        + "❌ [Release] Rights in projects — batch 1\n",
                "========================\n*Успешно пройденные тесты: \n*\n========================\n"
                        + "✅ [Release] Authorisation smoke\n",
                "========================\n*Тесты, которые не были запущены из-за системной ошибки: \n*\n========================\n"
                        + "➡️ [DAST] No token\n➡️ [OSA] IC check skipped\n");
        seedRun("2148", "seed-payloads/updates-summary.txt", failed(),
                "========================\n*Успешно пройденные тесты: \n*\n========================\n"
                        + "✅ [SCA] CVE database sync\n✅ [SCS] Ruleset import v2.4\n",
                skipped());
        seedRun("2158", "seed-payloads/ai-summary.txt",
                "========================\n*Упавшие тесты с ошибкой: \n*\n========================\n"
                        + "❌ [AI] Model inference timeout\n❌ [AI] False positive rate\n❌ [AI] Prompt injection #7\n",
                "========================\n*Успешно пройденные тесты: \n*\n========================\n"
                        + "✅ [AI] Baseline scan completed\n",
                skipped());
        seedRun("1", "seed-payloads/general-summary.txt",
                "========================\n*Упавшие тесты с ошибкой: \n*\n========================\n"
                        + "❌ [Local] Config load error\n❌ [Local] Driver mismatch\n",
                "========================\n*Успешно пройденные тесты: \n*\n========================\n"
                        + "✅ [Local] Open app\n✅ [Local] Logout\n",
                skipped());
        seedRun("local", "seed-payloads/local-summary.txt",
                "========================\n*Упавшие тесты с ошибкой: \n*\n========================\n"
                        + "❌ [Local] Config load error\n",
                "========================\n*Успешно пройденные тесты: \n*\n========================\n"
                        + "✅ [Local] Open dashboard\n✅ [Local] API ping\n✅ [Local] Logout\n",
                "========================\n*Тесты, которые не были запущены из-за системной ошибки: \n*\n========================\n"
                        + "➡️ [Local] Stand unavailable\n");
        seedSummaryRun();
    }

    private void seedSummaryRun() throws Exception {
        String runId = storage.save(read("seed-payloads/summary-daily.txt"), "Markdown", "2156", null);
        storage.save(read("seed-payloads/summary-suites-ok.txt"), "Markdown", "2156", runId);
        storage.save(read("seed-payloads/summary-suites-fail.txt"), "Markdown", "2156", runId);
    }

    private void seedRun(String threadId, String summaryResource, String failed, String passed, String skipped)
            throws Exception {
        String runId = storage.save(read(summaryResource), "Markdown", threadId, null);
        if (!failed.isBlank()) {
            storage.save(failed, "Markdown", threadId, runId);
        }
        if (!passed.isBlank()) {
            storage.save(passed, "Markdown", threadId, runId);
        }
        if (!skipped.isBlank()) {
            storage.save(skipped, "Markdown", threadId, runId);
        }
    }

    private String read(String classpath) throws Exception {
        ClassPathResource res = new ClassPathResource(classpath);
        return new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String failed() {
        return "";
    }

    private static String skipped() {
        return "";
    }
}
