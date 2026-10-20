package com.appscreener.report.model.allure;

/**
 * Ссылка на attachment в формате allure-results ({@code {name, source, type}}).
 */
public record AllureAttachmentRef(String name, String source, String type) {

    public boolean isImage() {
        return type != null && type.startsWith("image/");
    }
}
