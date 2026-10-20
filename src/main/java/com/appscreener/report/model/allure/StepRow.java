package com.appscreener.report.model.allure;

/**
 * Развёрнутая строка дерева шагов: узел + глубина (для плоского рендера в шаблоне).
 */
public record StepRow(AllureStepNode step, int depth) {
}
