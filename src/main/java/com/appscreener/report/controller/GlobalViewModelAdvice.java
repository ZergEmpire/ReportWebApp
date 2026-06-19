package com.appscreener.report.controller;

import com.appscreener.report.service.AccessControlService;
import com.appscreener.report.support.DisplayTimeFormatter;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalViewModelAdvice {

    private final AccessControlService accessControlService;
    private final DisplayTimeFormatter displayTimeFormatter;

    public GlobalViewModelAdvice(AccessControlService accessControlService,
                                 DisplayTimeFormatter displayTimeFormatter) {
        this.accessControlService = accessControlService;
        this.displayTimeFormatter = displayTimeFormatter;
    }

    @ModelAttribute("authEnabled")
    public boolean authEnabled() {
        return accessControlService.isAuthEnabled();
    }

    @ModelAttribute("isAdminSession")
    public boolean isAdminSession(HttpSession session) {
        return accessControlService.isAdminSession(session);
    }

    @ModelAttribute("displayTime")
    public DisplayTimeFormatter displayTime() {
        return displayTimeFormatter;
    }

}
