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

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
