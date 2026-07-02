package com.appscreener.report.service;

import com.appscreener.report.model.AllureAttachmentMeta;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Выбор скриншота падения среди вложений test result в Allure TestOps.
 */
final class AllureAttachmentSelector {

    private AllureAttachmentSelector() {
    }

    static Optional<AllureAttachmentMeta> pickScreenshot(List<AllureAttachmentMeta> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Optional.empty();
        }
        return attachments.stream()
                .filter(AllureAttachmentSelector::looksLikeImage)
                .max(Comparator.comparingInt(AllureAttachmentSelector::screenshotScore)
                        .thenComparing(AllureAttachmentMeta::getId));
    }

    static Optional<AllureAttachmentMeta> pickMainAttachment(List<AllureAttachmentMeta> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Optional.empty();
        }
        return attachments.stream()
                .filter(AllureAttachmentSelector::looksLikeMainAttachment)
                .max(Comparator.comparingInt(AllureAttachmentSelector::mainAttachmentScore)
                        .thenComparing(AllureAttachmentMeta::getId));
    }

    static boolean looksLikeImage(AllureAttachmentMeta attachment) {
        if (attachment == null) {
            return false;
        }
        String contentType = normalize(attachment.getContentType());
        if (contentType.startsWith("image/")) {
            return true;
        }
        String name = normalize(attachment.getName());
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp");
    }

    static boolean looksLikeMainAttachment(AllureAttachmentMeta attachment) {
        if (attachment == null || looksLikeImage(attachment)) {
            return false;
        }
        String contentType = normalize(attachment.getContentType());
        String name = normalize(attachment.getName());
        return contentType.contains("html")
                || contentType.startsWith("text/")
                || contentType.contains("json")
                || contentType.contains("xml")
                || contentType.contains("csv")
                || contentType.contains("markdown")
                || name.endsWith(".html")
                || name.endsWith(".htm")
                || name.endsWith(".txt")
                || name.endsWith(".log")
                || name.endsWith(".csv")
                || name.endsWith(".md")
                || name.endsWith(".json");
    }

    static int screenshotScore(AllureAttachmentMeta attachment) {
        if (!looksLikeImage(attachment)) {
            return Integer.MIN_VALUE;
        }
        String name = normalize(attachment.getName());
        int score = 0;
        if (name.contains("screenshot") || name.contains("screen shot") || name.contains("screen_shot")) {
            score += 20;
        }
        if (name.contains("failure") || name.contains("failed") || name.contains("error")) {
            score += 10;
        }
        if (name.contains("page")) {
            score += 5;
        }
        if (name.contains("png") || name.contains("jpg") || name.contains("jpeg")) {
            score += 1;
        }
        return score;
    }

    static int mainAttachmentScore(AllureAttachmentMeta attachment) {
        if (!looksLikeMainAttachment(attachment)) {
            return Integer.MIN_VALUE;
        }
        String name = normalize(attachment.getName());
        String contentType = normalize(attachment.getContentType());
        int score = 0;
        if (contentType.contains("html") || name.endsWith(".html") || name.endsWith(".htm")) {
            score += 40;
        }
        if (name.contains("metric") || name.contains("table") || name.contains("report")) {
            score += 20;
        }
        if (name.contains("summary") || name.contains("overview") || name.contains("attachment")) {
            score += 10;
        }
        if (contentType.startsWith("text/")) {
            score += 5;
        }
        return score;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
