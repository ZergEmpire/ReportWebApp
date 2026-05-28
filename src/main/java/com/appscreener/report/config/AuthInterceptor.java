package com.appscreener.report.config;

import com.appscreener.report.service.AccessControlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AccessControlService accessControlService;

    public AuthInterceptor(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!accessControlService.isAuthEnabled()) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (accessControlService.isAuthorizedSession(session)) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Требуется авторизация\"}");
            return false;
        }
        response.sendRedirect("/auth/login");
        return false;
    }
}
