package com.appscreener.report.service;

import com.appscreener.report.entity.TestRunEntity;
import com.appscreener.report.model.ParsedReport;
import com.appscreener.report.model.ReportType;
import com.appscreener.report.parser.MarkdownReportParser;
import com.appscreener.report.repository.ReportCategoryRepository;
import com.appscreener.report.repository.ReportMessageRepository;
import com.appscreener.report.repository.TestRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Регрессия: служебное сообщение со ссылкой на Report Web App не должно обнулять сводку прогона.
 */
class ReportStorageServiceSummaryMergeTest {

    private MarkdownReportParser parser;

    @BeforeEach
    void setUp() {
        ReportCategoryRepository categoryRepo = mock(ReportCategoryRepository.class);
        when(categoryRepo.findAllByOrderBySortOrderAscLabelAsc()).thenReturn(List.of());
        parser = new MarkdownReportParser(new CategoryService(categoryRepo));
    }

    @Test
    void unknownLinkMessageMustNotClearSummaryFields() throws Exception {
        TestRunRepository runRepo = mock(TestRunRepository.class);
        when(runRepo.save(any(TestRunEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ReportCategoryRepository categoryRepo = mock(ReportCategoryRepository.class);
        when(categoryRepo.findAllByOrderBySortOrderAscLabelAsc()).thenReturn(List.of());
        ReportStorageService service = new ReportStorageService(
                runRepo,
                mock(ReportMessageRepository.class),
                parser,
                mock(AllureTestOpsService.class),
                new CategoryService(categoryRepo));

        String summaryText = """
                ☑️ Результаты тестирования приложения appScreener.
                *Стенд, где проходил автотест: \n*
                `https://stand.example.com`
                *Статистика:\n*
                Всего тестов: 5
                Успешных: 4
                Проваленных (ошибка): 1
                Не запущенных (системная ошибка): 0
                """;
        ParsedReport summary = parser.parse(summaryText, "release");

        TestRunEntity run = new TestRunEntity();
        run.setId("abc123");
        run.setCategoryCode("release");

        invokeApplySummary(service, run, summary);
        assertEquals("https://stand.example.com", run.getStandUrl());
        assertEquals(5, run.getTotalTests());

        String linkOnly = "[📋 Отчёт в Report Web App](https://host/report/abc123)";
        ParsedReport link = parser.parse(linkOnly, "release");
        assertEquals(ReportType.UNKNOWN, link.getReportType());

        invokeApplySummary(service, run, link);

        assertEquals("https://stand.example.com", run.getStandUrl());
        assertEquals(5, run.getTotalTests());
        assertNotNull(run.getPassedTests());
        assertEquals(4, run.getPassedTests());
    }

    private static void invokeApplySummary(ReportStorageService service, TestRunEntity run, ParsedReport parsed)
            throws Exception {
        Method m = ReportStorageService.class.getDeclaredMethod("applySummaryFields", TestRunEntity.class, ParsedReport.class);
        m.setAccessible(true);
        m.invoke(service, run, parsed);
    }
}
