package com.appscreener.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "report.auth")
public class AuthProperties {

    private boolean enabled = true;
    private String adminUsername = "admin";
    private String adminPassword = "1337ZergEmpire!";
    private String adminContactEmail = "s.kavalerov@rt-solar.ru";
    private String initialAccessKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getAdminContactEmail() {
        return adminContactEmail;
    }

    public void setAdminContactEmail(String adminContactEmail) {
        this.adminContactEmail = adminContactEmail;
    }

    public String getInitialAccessKey() {
        return initialAccessKey;
    }

    public void setInitialAccessKey(String initialAccessKey) {
        this.initialAccessKey = initialAccessKey;
    }
}
