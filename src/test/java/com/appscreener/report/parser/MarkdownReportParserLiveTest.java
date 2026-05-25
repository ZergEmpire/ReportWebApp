package com.appscreener.report.parser;

import com.appscreener.report.model.ParsedReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarkdownReportParserLiveTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void saveAndLoadExpressRun() {
        String summary = """
                ☑️ Результаты тестирования приложения appScreener.
                ========================
                *Стенд, где проходил автотест: \\n*
                `https://appscreener-ui01d.ast.rt-solar.ru`
                *Название набора: \\n*
                `Test Suite`
                *Значение переменной CI_SUITES для запуска Pipeline:\\n*
                `Express/ExpressOsa.xml`
                ========================
                *Статистика:\\n*
                Всего тестов: 17
                Успешных: 13
                Проваленных (ошибка): 4
                Не запущенных (системная ошибка): 0
                Время прохождения всех тестов: 00:23:03
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("text", summary);
        body.add("parse_mode", "Markdown");
        body.add("message_thread_id", "2154");

        ResponseEntity<Map> resp = rest.postForEntity(
                "http://localhost:" + port + "/sendMessage",
                new HttpEntity<>(body, headers),
                Map.class);
        String runId = (String) ((Map) resp.getBody().get("result")).get("message_id");

        Map<?, ?> run = rest.getForObject("http://localhost:" + port + "/api/runs/" + runId, Map.class);
        assertEquals(17, run.get("totalTests"));
        assertNotNull(run.get("standUrl"));
    }
}
