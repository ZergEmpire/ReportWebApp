package com.appscreener.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "report.allure")
public class AllureTestOpsProperties {

    /** https://dersecur.testops.cloud */
    private String baseUrl = "https://dersecur.testops.cloud";

    /** API token из профиля Allure TestOps (User menu → API Tokens). */
    private String apiToken = "";

    private boolean enabled = true;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public boolean isEnabled() {
        return enabled && apiToken != null && !apiToken.isBlank();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String apiBase() {
        String url = baseUrl == null ? "" : baseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
