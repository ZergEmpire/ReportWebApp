package com.appscreener.report.model.allure;

import java.util.List;

/**
 * Узел дерева шагов allure (step может содержать вложенные step-ы, свои параметры и аттачи).
 */
public record AllureStepNode(
        String name,
        String status,
        String stage,
        Long start,
        Long stop,
        String message,
        String trace,
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
