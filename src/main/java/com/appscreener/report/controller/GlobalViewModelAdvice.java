package com.appscreener.report.controller;

import com.appscreener.report.service.AccessControlService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalViewModelAdvice {

    private final AccessControlService accessControlService;

    public GlobalViewModelAdvice(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    @ModelAttribute("authEnabled")
    public boolean authEnabled() {
        return accessControlService.isAuthEnabled();
    }

    @ModelAttribute("isAdminSession")
    public boolean isAdminSession(HttpSession session) {
        return accessControlService.isAdminSession(session);
    }
}
