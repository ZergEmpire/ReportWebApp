package com.appscreener.report.model.allure;

import java.util.List;
import java.util.Map;

/**
 * Полный результат теста (для страницы теста): дерево шагов, аттачи, параметры, лейблы.
 */
public record AllureRichResult(
        String uuid,
        String name,
        String fullName,
        String status,
        boolean known,
        boolean flaky,
        boolean muted,
        String statusMessage,
        String statusTrace,
        Long start,
        Long stop,
        Map<String, List<String>> labels,
        List<AllureStepNode> steps,
        List<AllureAttachmentRef> attachments,
        List<AllureParam> parameters) {

    public long durationMs() {
        if (start == null || stop == null || stop < start) {
            return 0;
        }
        return stop - start;
    }
}
