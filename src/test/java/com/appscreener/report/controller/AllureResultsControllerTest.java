package com.appscreener.report.controller;

import com.appscreener.report.repository.TestRunRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AllureResultsControllerTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("report.allure-results.storage-dir", () -> tempDir.resolve("allure").toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestRunRepository testRunRepository;

    @Test
    void uploadImportsRunAndReturnsTelegramStyleResponse() throws Exception {
        mockMvc.perform(multipart("/allure-results")
                        .file(new MockMultipartFile("archive", "allure-results.zip", "application/zip",
                                sampleZipBytes()))
                        .param("message_thread_id", "2154")
                        .param("stand_url", "https://stand.example.com")
                        .param("ci_suites", "Express/All.xml"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.result.run_id").isNotEmpty())
                .andExpect(jsonPath("$.result.report_url").isNotEmpty())
                .andExpect(jsonPath("$.result.summary.total").value(3))
                .andExpect(jsonPath("$.result.summary.passed").value(1))
                .andExpect(jsonPath("$.result.summary.failed").value(1))
                .andExpect(jsonPath("$.result.summary.broken").value(0))
                .andExpect(jsonPath("$.result.summary.skipped").value(1))
                .andExpect(jsonPath("$.result.tests.length()").value(3));
    }

    @Test
    void uploadWithoutArchiveFails() throws Exception {
        mockMvc.perform(multipart("/allure-results")
                        .file(new MockMultipartFile("archive", "empty.zip", "application/zip", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    void attachmentContentRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/allure/attachments/some-source.png"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importedRunStoredWithCategory() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/allure-results")
                        .file(new MockMultipartFile("archive", "allure-results.zip", "application/zip",
                                sampleZipBytes()))
                        .param("message_thread_id", "2154"))
                .andExpect(status().isOk())
                .andReturn();

        String runId = JsonPath.read(result.getResponse().getContentAsString(), "$.result.run_id");
        assertTrue(testRunRepository.findById(runId).isPresent());
        assertEquals("express", testRunRepository.findById(runId).orElseThrow().getCategoryCode());
    }

    private static byte[] sampleZipBytes() throws Exception {
        Path dir = Paths.get(AllureResultsControllerTest.class
                .getResource("/allure-results-sample").toURI());
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf);
             var files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
                zip.write(Files.readAllBytes(file));
                zip.closeEntry();
            }
        }
        return buf.toByteArray();
    }
}
