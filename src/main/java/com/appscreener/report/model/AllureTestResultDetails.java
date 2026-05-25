package com.appscreener.report.model;

public class AllureTestResultDetails {

    private long testResultId;
    private String name;
    private String status;
    private String message;
    private String trace;
    private String allureUiUrl;

    public long getTestResultId() {
        return testResultId;
    }

    public void setTestResultId(long testResultId) {
        this.testResultId = testResultId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        this.trace = trace;
    }

    public String getAllureUiUrl() {
        return allureUiUrl;
    }

    public void setAllureUiUrl(String allureUiUrl) {
        this.allureUiUrl = allureUiUrl;
    }
}
