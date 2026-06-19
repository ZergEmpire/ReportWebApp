package com.appscreener.report.support;

import com.appscreener.report.config.DisplayTimeProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Форматирование {@link Instant} для UI в заданном часовом поясе (по умолчанию МСК).
 */
@Component("displayTime")
public class DisplayTimeFormatter {

    private final ZoneId zoneId;
    private final DateTimeFormatter defaultFormatter;

    public DisplayTimeFormatter(DisplayTimeProperties properties) {
        this.zoneId = ZoneId.of(properties.getTimeZone());
        this.defaultFormatter = DateTimeFormatter.ofPattern(properties.getDateTimePattern()).withZone(zoneId);
    }

    public String format(Instant instant) {
        if (instant == null) {
            return "";
        }
        return defaultFormatter.format(instant);
    }

    public String format(Instant instant, String pattern) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern(pattern).withZone(zoneId).format(instant);
    }

    /** Подпись часового пояса для подсказок в UI, например «МСК». */
    public String zoneShortLabel() {
        return zoneId.getId().equals("Europe/Moscow") ? "МСК" : zoneId.getId();
    }
}
