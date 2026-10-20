package com.appscreener.report.service;

import com.appscreener.report.model.allure.AllureParsedResult;
import com.appscreener.report.model.allure.AllureParsedRun;
import com.appscreener.report.model.allure.AllureRichResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllureResultsParserTest {

    private static final String PASSED = "11111111-1111-1111-1111-111111111111";
    private static final String FAILED = "22222222-2222-2222-2222-222222222222";
    private static final String SKIPPED = "33333333-3333-3333-3333-333333333333";

    static Path sampleDir() throws URISyntaxException {
        return Paths.get(AllureResultsParserTest.class
                .getResource("/allure-results-sample").toURI());
    }

    @Test
    void parsesSummaryCountsAndSuites() throws Exception {
        AllureParsedRun run = new AllureResultsParser().parseDirectory(sampleDir());

        assertEquals(3, run.results().size());
        assertEquals(1, run.passedCount());
        assertEquals(1, run.failedCount());
        assertEquals(1, run.skippedCount());
        // контейнер «Сюита примеров» + label-fallback «Smoke» для теста вне контейнера
        assertTrue(run.suites().contains("Сюита примеров"));
        assertTrue(run.suites().contains("Smoke"));
        assertEquals(1792481587000L, run.minStart());
        assertEquals(1792481590100L, run.maxStop());
    }

    @Test
    void suiteResolvedFromContainerForChildrenAndFromLabelsForOrphans() throws Exception {
        AllureParsedRun run = new AllureResultsParser().parseDirectory(sampleDir());

        AllureParsedResult passed = findByUuid(run, PASSED);
        AllureParsedResult failed = findByUuid(run, FAILED);
        AllureParsedResult skipped = findByUuid(run, SKIPPED);

        assertEquals("Сюита примеров", passed.suiteName());
        assertEquals("Сюита примеров", failed.suiteName());
        assertEquals("Smoke", skipped.suiteName());
    }

    @Test
    void resultsSortedByStartAndStatusDetailsRead() throws Exception {
        AllureParsedRun run = new AllureResultsParser().parseDirectory(sampleDir());

        assertEquals(SKIPPED, run.results().get(0).uuid());
        assertEquals(PASSED, run.results().get(1).uuid());
        assertEquals(FAILED, run.results().get(2).uuid());

        AllureParsedResult failed = findByUuid(run, FAILED);
        assertTrue(failed.failedLike());
        assertNotNull(failed.message());
        assertTrue(failed.message().contains("Element should have text"));
    }

    @Test
    void fullResultContainsStepsTreeParamsAndAttachments() throws Exception {
        AllureRichResult rich = new AllureResultsParser()
                .parseFullResult(sampleDir().resolve(FAILED + "-result.json"));

        assertEquals("failed", rich.status());
        assertTrue(rich.statusTrace().contains("com.codeborne.selenide.ex.ElementShould"));
        assertEquals(1, rich.steps().size());
        assertEquals(1, rich.steps().get(0).attachments().size());
        assertEquals("image/png", rich.steps().get(0).attachments().get(0).type());
        assertEquals(1, rich.attachments().size());

        AllureRichResult passedRich = new AllureResultsParser()
                .parseFullResult(sampleDir().resolve(PASSED + "-result.json"));
        assertEquals(1, passedRich.steps().size());
        assertEquals(1, passedRich.steps().get(0).steps().size());
        assertEquals("200", passedRich.steps().get(0).steps().get(0).parameters().get(0).value());
        assertEquals("Модуль API", passedRich.labels().get("epic").get(0));
    }

    @Test
    void attachmentFilesCollectedByName() throws Exception {
        Map<String, Path> files = new AllureResultsParser().attachmentFiles(sampleDir());

        assertEquals(3, files.size());
        assertTrue(files.containsKey("aaaaaaaa-0000-0000-0000-000000000000-attachment.md"));
        assertTrue(files.containsKey("bbbbbbbb-0000-0000-0000-000000000000-attachment.png"));
        assertTrue(files.containsKey("cccccccc-0000-0000-0000-000000000000-attachment.txt"));
    }

    @Test
    void attachmentRefsProvideNameAndTypeFromJson() throws Exception {
        Map<String, com.appscreener.report.model.allure.AllureAttachmentRef> refs =
                new AllureResultsParser().attachmentRefs(sampleDir());

        assertEquals("text/markdown", refs.get("aaaaaaaa-0000-0000-0000-000000000000-attachment.md").type());
        assertEquals("Метрики прогона", refs.get("aaaaaaaa-0000-0000-0000-000000000000-attachment.md").name());
        assertEquals("image/png", refs.get("bbbbbbbb-0000-0000-0000-000000000000-attachment.png").type());
        // реф из container (befores)
        assertEquals("text/plain", refs.get("cccccccc-0000-0000-0000-000000000000-attachment.txt").type());
        // реф из вложенного шага тоже собирается
        assertEquals("image/png", refs.get("bbbbbbbb-0000-0000-0000-000000000000-attachment.png").type());
    }

    @Test
    void emptyDirectoryProducesEmptyRun() throws Exception {
        Path empty = java.nio.file.Files.createTempDirectory("allure-empty");
        try {
            AllureParsedRun run = new AllureResultsParser().parseDirectory(empty);
            assertEquals(0, run.results().size());
            assertEquals(0, run.minStart());
        } finally {
            deleteRecursive(empty);
        }
    }

    private static AllureParsedResult findByUuid(AllureParsedRun run, String uuid) {
        return run.results().stream()
                .filter(r -> uuid.equals(r.uuid()))
                .findFirst().orElseThrow();
    }

    private static void deleteRecursive(Path path) throws IOException {
        try (var walk = java.nio.file.Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
