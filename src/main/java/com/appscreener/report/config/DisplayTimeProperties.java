package com.appscreener.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "report.display")
public class DisplayTimeProperties {

    /** Часовой пояс отображения дат в UI (хранение в БД — UTC Instant). */
    private String timeZone = "Europe/Moscow";

    private String dateTimePattern = "dd.MM.yyyy HH:mm";

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getDateTimePattern() {
        return dateTimePattern;
    }

    public void setDateTimePattern(String dateTimePattern) {
        this.dateTimePattern = dateTimePattern;
    }
}
