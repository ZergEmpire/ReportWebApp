package com.appscreener.report.service;

import com.appscreener.report.model.AllureAttachmentMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllureAttachmentSelectorTest {

    @Test
    void picksScreenshotByNameAndContentType() {
        AllureAttachmentMeta log = meta(1, "log.txt", "text/plain");
        AllureAttachmentMeta screen = meta(2, "failure-screenshot.png", "image/png");
        AllureAttachmentMeta otherImage = meta(3, "diagram.png", "image/png");

        AllureAttachmentMeta picked = AllureAttachmentSelector.pickScreenshot(
                List.of(log, otherImage, screen)).orElseThrow();

        assertEquals(2L, picked.getId());
    }

    @Test
    void returnsEmptyWhenNoImages() {
        assertTrue(AllureAttachmentSelector.pickScreenshot(
                List.of(meta(1, "log.txt", "text/plain"))).isEmpty());
    }

    @Test
    void picksHtmlAttachmentAsMainContent() {
        AllureAttachmentMeta image = meta(1, "failure-screenshot.png", "image/png");
        AllureAttachmentMeta text = meta(2, "run.log", "text/plain");
        AllureAttachmentMeta html = meta(3, "metrics-report.html", "text/html");

        AllureAttachmentMeta picked = AllureAttachmentSelector.pickMainAttachment(
                List.of(image, text, html)).orElseThrow();

        assertEquals(3L, picked.getId());
    }

    @Test
    void mainAttachmentReturnsEmptyWhenOnlyImages() {
        assertTrue(AllureAttachmentSelector.pickMainAttachment(
                List.of(meta(1, "failure-screenshot.png", "image/png"))).isEmpty());
    }

    private static AllureAttachmentMeta meta(long id, String name, String contentType) {
        AllureAttachmentMeta meta = new AllureAttachmentMeta();
        meta.setId(id);
        meta.setName(name);
        meta.setContentType(contentType);
        return meta;
    }
}
