package com.appscreener.report.model;

public class TestLineItem {

    private String icon;
    private String name;
    private String url;
    private String path;
    private String meta;
    /** ID test result в Allure TestOps (из ссылки launch/.../tree/{id}). */
    private Long allureTestResultId;

    public TestLineItem() {
    }

    public TestLineItem(String icon, String name, String url) {
        this.icon = icon;
        this.name = name;
        this.url = url;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }

    public Long getAllureTestResultId() {
        return allureTestResultId;
    }

    public void setAllureTestResultId(Long allureTestResultId) {
        this.allureTestResultId = allureTestResultId;
    }
}
