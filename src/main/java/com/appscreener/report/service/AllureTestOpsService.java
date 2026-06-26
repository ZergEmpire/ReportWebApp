package com.appscreener.report.service;

import com.appscreener.report.config.AllureTestOpsProperties;
import com.appscreener.report.model.AllureAttachmentMeta;
import com.appscreener.report.model.AllureTestResultDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Клиент Report Service Allure TestOps (тот же API, что в {@code ApiAllureMethods} / DashboardsForExpressTests).
 */
@Service
public class AllureTestOpsService {

    private static final Pattern TEST_RESULT_ID_IN_URL = Pattern.compile(
            "(?:/launch/\\d+/tree/|/testresult/)(\\d+)", Pattern.CASE_INSENSITIVE);

    private final AllureTestOpsProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public AllureTestOpsService(AllureTestOpsProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.isEnabled();
    }

    public Optional<Long> parseTestResultId(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        Matcher m = TEST_RESULT_ID_IN_URL.matcher(url);
        if (m.find()) {
            return Optional.of(Long.parseLong(m.group(1)));
        }
        return Optional.empty();
    }

    public AllureTestResultDetails fetchTestResult(long testResultId) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException(
                    "Allure TestOps не настроен: задайте report.allure.api-token (или ALLURE_TESTOPS_TOKEN)");
        }
        String token = obtainAccessToken();
        String apiUrl = properties.apiBase() + "/api/testresult/" + testResultId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(MediaType.parseMediaTypes("application/json"));

        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(
                    "Allure TestOps ответил " + response.getStatusCode() + " для testresult/" + testResultId);
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            AllureTestResultDetails details = new AllureTestResultDetails();
            details.setTestResultId(testResultId);
            details.setName(textOrNull(root, "name"));
            details.setStatus(textOrNull(root, "status"));
            details.setMessage(firstNonBlank(
                    textOrNull(root, "message"),
                    pathText(root, "statusDetails", "message")
            ));
            details.setTrace(firstNonBlank(
                    textOrNull(root, "trace"),
                    pathText(root, "statusDetails", "trace")
            ));
            details.setAllureUiUrl(properties.apiBase() + "/testresult/" + testResultId);
            return details;
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось разобрать ответ Allure TestOps: " + e.getMessage(), e);
        }
    }

    public List<AllureAttachmentMeta> listTestResultAttachments(long testResultId) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException(
                    "Allure TestOps не настроен: задайте report.allure.api-token (или ALLURE_TESTOPS_TOKEN)");
        }
        String token = obtainAccessToken();
        String apiUrl = properties.apiBase() + "/api/testresult/attachment?testResultId=" + testResultId + "&size=100";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(MediaType.parseMediaTypes("application/json"));

        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(
                    "Allure TestOps ответил " + response.getStatusCode()
                            + " для вложений testresult/" + testResultId);
        }

        try {
            return parseAttachmentList(objectMapper.readTree(response.getBody()));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось разобрать список вложений Allure TestOps: " + e.getMessage(), e);
        }
    }

    public Optional<AllureAttachmentMeta> findScreenshotAttachment(long testResultId) {
        return AllureAttachmentSelector.pickScreenshot(listTestResultAttachments(testResultId));
    }

    public byte[] fetchAttachmentContent(long attachmentId) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException(
                    "Allure TestOps не настроен: задайте report.allure.api-token (или ALLURE_TESTOPS_TOKEN)");
        }
        String token = obtainAccessToken();
        String apiUrl = properties.apiBase() + "/api/testresult/attachment/" + attachmentId + "/content";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(
                    "Allure TestOps ответил " + response.getStatusCode() + " для attachment/" + attachmentId);
        }
        return response.getBody();
    }

    private List<AllureAttachmentMeta> parseAttachmentList(JsonNode root) {
        List<AllureAttachmentMeta> result = new ArrayList<>();
        JsonNode items = root;
        if (root.has("content") && root.get("content").isArray()) {
            items = root.get("content");
        } else if (!root.isArray()) {
            return result;
        }
        for (JsonNode node : items) {
            if (node == null || node.isNull()) {
                continue;
            }
            long id = node.path("id").asLong(0);
            if (id <= 0) {
                continue;
            }
            AllureAttachmentMeta meta = new AllureAttachmentMeta();
            meta.setId(id);
            meta.setName(textOrNull(node, "name"));
            meta.setContentType(firstNonBlank(
                    textOrNull(node, "contentType"),
                    textOrNull(node, "mimeType"),
                    textOrNull(node, "type")
            ));
            if (node.has("contentLength") && !node.get("contentLength").isNull()) {
                meta.setSizeBytes(node.get("contentLength").asLong());
            } else if (node.has("size") && !node.get("size").isNull()) {
                meta.setSizeBytes(node.get("size").asLong());
            }
            result.add(meta);
        }
        return result;
    }

    private synchronized String obtainAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        String tokenUrl = properties.apiBase() + "/api/uaa/oauth/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(MediaType.parseMediaTypes("application/json"));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "apitoken");
        form.add("scope", "openid");
        form.add("token", properties.getApiToken());

        ResponseEntity<String> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Не удалось получить OAuth-токен Allure TestOps: " + response.getStatusCode());
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            cachedToken = root.path("access_token").asText(null);
            if (cachedToken == null || cachedToken.isBlank()) {
                throw new IllegalStateException("Пустой access_token в ответе Allure TestOps");
            }
            long expiresIn = root.path("expires_in").asLong(3600);
            tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 120));
            return cachedToken;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Ошибка разбора OAuth Allure TestOps: " + e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    private static String pathText(JsonNode node, String... path) {
        JsonNode cur = node;
        for (String p : path) {
            if (cur == null) {
                return null;
            }
            cur = cur.get(p);
        }
        if (cur == null || cur.isNull()) {
            return null;
        }
        String s = cur.asText();
        return s.isBlank() ? null : s;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
