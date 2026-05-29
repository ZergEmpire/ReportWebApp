package com.appscreener.report.controller;

import com.appscreener.report.model.CategoryIcons;
import com.appscreener.report.service.AccessControlService;
import com.appscreener.report.service.CategoryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final AccessControlService accessControlService;
    private final CategoryService categoryService;

    public AuthController(AccessControlService accessControlService, CategoryService categoryService) {
        this.accessControlService = accessControlService;
        this.categoryService = categoryService;
    }

    @GetMapping("/auth/login")
    public String loginPage(Model model, HttpSession session) {
        if (accessControlService.isAuthorizedSession(session)) {
            return "redirect:/";
        }
        model.addAttribute("adminContactEmail", accessControlService.getAdminContactEmail());
        return "auth-login";
    }

    @PostMapping("/auth/access")
    public String accessLogin(
            @RequestParam("accessKey") String accessKey,
            HttpSession session,
            RedirectAttributes flash) {
        if (accessControlService.authenticateByAccessKey(accessKey, session)) {
            return "redirect:/";
        }
        flash.addFlashAttribute("message", "Неверный ключ доступа");
        flash.addFlashAttribute("messageType", "error");
        return "redirect:/auth/login";
    }

    @PostMapping("/auth/admin")
    public String adminLogin(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpSession session,
            RedirectAttributes flash) {
        if (accessControlService.authenticateAdmin(username, password, session)) {
            return "redirect:/auth/admin/key";
        }
        flash.addFlashAttribute("message", "Неверный логин или пароль администратора");
        flash.addFlashAttribute("messageType", "error");
        return "redirect:/auth/login";
    }

    @GetMapping("/auth/admin/key")
    public String adminKeyPage(HttpSession session, Model model) {
        if (!accessControlService.isAdminSession(session)) {
            return "redirect:/";
        }
        model.addAttribute("activeKey", accessControlService.getActiveAccessKey());
        model.addAttribute("keyVersion", accessControlService.getActiveKeyVersion());
        return "auth-admin";
    }

    @PostMapping("/auth/admin/key/rotate")
    public String rotateKey(HttpSession session, RedirectAttributes flash) {
        if (!accessControlService.isAdminSession(session)) {
            return "redirect:/";
        }
        String newKey = accessControlService.rotateAccessKey();
        flash.addFlashAttribute("message", "Новый ключ сгенерирован: " + newKey);
        flash.addFlashAttribute("messageType", "warn");
        return "redirect:/auth/admin/key";
    }

    @GetMapping("/auth/admin/categories")
    public String adminCategoriesPage(HttpSession session, Model model) {
        if (!accessControlService.isAdminSession(session)) {
            return "redirect:/";
        }
        model.addAttribute("customCategories", categoryService.customCategories());
        model.addAttribute("iconOptions", CategoryIcons.PICKER_OPTIONS);
        return "auth-admin-categories";
    }

    @PostMapping("/auth/admin/categories")
    public String createCategory(
            HttpSession session,
            @RequestParam("code") String code,
            @RequestParam("threadId") String threadId,
            @RequestParam("label") String label,
            @RequestParam("icon") String icon,
            RedirectAttributes flash) {
        if (!accessControlService.isAdminSession(session)) {
            return "redirect:/";
        }
        try {
            var created = categoryService.createCustom(code, threadId, label, icon);
            flash.addFlashAttribute("message",
                    "Категория создана: " + created.displayName()
                            + " — в автотестах укажите message_thread_id=" + created.threadId());
            flash.addFlashAttribute("messageType", "ok");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("message", ex.getMessage());
            flash.addFlashAttribute("messageType", "error");
        }
        return "redirect:/auth/admin/categories";
    }

    @PostMapping("/auth/logout")
    public String logout(HttpSession session) {
        accessControlService.logout(session);
        return "redirect:/auth/login";
    }
}
