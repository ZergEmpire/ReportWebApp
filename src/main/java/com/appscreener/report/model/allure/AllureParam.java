package com.appscreener.report.model.allure;

/**
 * Параметр шага/теста в формате allure-results ({@code {name, value}}).
 */
public record AllureParam(String name, String value) {
}
