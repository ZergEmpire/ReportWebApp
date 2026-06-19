package com.appscreener.report.support;

import com.appscreener.report.config.DisplayTimeProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayTimeFormatterTest {

    @Test
    void formatsInstantInMoscow() {
        DisplayTimeProperties props = new DisplayTimeProperties();
        props.setTimeZone("Europe/Moscow");
        props.setDateTimePattern("dd.MM.yyyy HH:mm");

        DisplayTimeFormatter formatter = new DisplayTimeFormatter(props);

        assertEquals("11.06.2026 22:43", formatter.format(Instant.parse("2026-06-11T19:43:54Z")));
        assertEquals("МСК", formatter.zoneShortLabel());
    }
}
