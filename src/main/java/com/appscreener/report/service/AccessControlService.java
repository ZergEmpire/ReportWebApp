package com.appscreener.report.service;

import com.appscreener.report.config.AuthProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

@Service
public class AccessControlService {

    public static final String SESSION_AUTHENTICATED = "auth.authenticated";
    public static final String SESSION_IS_ADMIN = "auth.isAdmin";
    public static final String SESSION_KEY_VERSION = "auth.keyVersion";

    private final AuthProperties authProperties;
    private final SecureRandom random = new SecureRandom();

    private volatile String activeAccessKey;
    private volatile long activeKeyVersion = 0;

    public AccessControlService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @PostConstruct
    public void initialize() {
        rotateAccessKey();
    }

    public boolean isAuthEnabled() {
        return authProperties.isEnabled();
    }

    public boolean authenticateByAccessKey(String key, HttpSession session) {
        if (!isAuthEnabled()) {
            return true;
        }
        String normalized = key != null ? key.trim() : "";
        if (normalized.isEmpty()) {
            return false;
        }
        String current = activeAccessKey;
        if (!Objects.equals(current, normalized)) {
            return false;
        }
        session.setAttribute(SESSION_AUTHENTICATED, true);
        session.setAttribute(SESSION_IS_ADMIN, false);
        session.setAttribute(SESSION_KEY_VERSION, activeKeyVersion);
        return true;
    }

    public boolean authenticateAdmin(String username, String password, HttpSession session) {
        if (!isAuthEnabled()) {
            return true;
        }
        boolean ok = Objects.equals(authProperties.getAdminUsername(), username)
                && Objects.equals(authProperties.getAdminPassword(), password);
        if (!ok) {
            return false;
        }
        session.setAttribute(SESSION_AUTHENTICATED, true);
        session.setAttribute(SESSION_IS_ADMIN, true);
        session.setAttribute(SESSION_KEY_VERSION, null);
        return true;
    }

    public boolean isAuthorizedSession(HttpSession session) {
        if (!isAuthEnabled()) {
            return true;
        }
        if (session == null) {
            return false;
        }
        Object authenticated = session.getAttribute(SESSION_AUTHENTICATED);
        if (!(authenticated instanceof Boolean auth) || !auth) {
            return false;
        }
        if (isAdminSession(session)) {
            return true;
        }
        Object version = session.getAttribute(SESSION_KEY_VERSION);
        return version instanceof Long v
                && v == activeKeyVersion
                && activeAccessKey != null
                && !activeAccessKey.isBlank();
    }

    public boolean isAdminSession(HttpSession session) {
        if (session == null) {
            return false;
        }
        Object admin = session.getAttribute(SESSION_IS_ADMIN);
        return admin instanceof Boolean b && b;
    }

    public void logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
    }

    public synchronized String rotateAccessKey() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        activeAccessKey = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        activeKeyVersion++;
        return activeAccessKey;
    }

    public String getActiveAccessKey() {
        return activeAccessKey;
    }

    public long getActiveKeyVersion() {
        return activeKeyVersion;
    }

    public String getAdminContactEmail() {
        return authProperties.getAdminContactEmail();
    }
}
