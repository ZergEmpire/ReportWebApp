package com.appscreener.report.service;

import com.appscreener.report.config.AllureTestOpsProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllureTestOpsServiceTest {

    @Test
    void parseTestResultIdFromLaunchTreeUrl() {
        AllureTestOpsProperties props = new AllureTestOpsProperties();
        AllureTestOpsService service = new AllureTestOpsService(props);

        assertEquals(
                5778L,
                service.parseTestResultId("https://dersecur.testops.cloud/launch/2003/tree/5778").orElseThrow());
        assertEquals(
                99L,
                service.parseTestResultId("https://dersecur.testops.cloud/testresult/99").orElseThrow());
        assertTrue(service.parseTestResultId("https://example.com/no-allure").isEmpty());
    }

    @Test
    void configuredOnlyWhenTokenPresent() {
        AllureTestOpsProperties props = new AllureTestOpsProperties();
        props.setEnabled(true);
        assertFalse(props.isEnabled());

        props.setApiToken("secret");
        assertTrue(props.isEnabled());
    }
}
