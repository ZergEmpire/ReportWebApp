package com.appscreener.report.service;

import com.appscreener.report.entity.ReportCategoryEntity;
import com.appscreener.report.model.CategoryIcons;
import com.appscreener.report.model.CategoryInfo;
import com.appscreener.report.model.ReportCategory;
import com.appscreener.report.repository.ReportCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class CategoryService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,30}$");
    private static final Pattern THREAD_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final ReportCategoryRepository repository;

    public CategoryService(ReportCategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CategoryInfo> navigable() {
        List<CategoryInfo> result = new ArrayList<>();
        result.add(CategoryInfo.fromEnum(ReportCategory.ALL));
        Arrays.stream(ReportCategory.values())
                .filter(c -> c != ReportCategory.ALL && c != ReportCategory.DEBUG)
                .map(CategoryInfo::fromEnum)
                .forEach(result::add);
        repository.findAllByOrderBySortOrderAscLabelAsc().stream()
                .map(this::toInfo)
                .forEach(result::add);
        result.add(CategoryInfo.fromEnum(ReportCategory.DEBUG));
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<CategoryInfo> customCategories() {
        return repository.findAllByOrderBySortOrderAscLabelAsc().stream()
                .map(this::toInfo)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CategoryInfo> resolveByThreadId(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return Optional.of(CategoryInfo.fromEnum(ReportCategory.GENERAL));
        }
        String normalized = threadId.trim();
        if ("localstart".equalsIgnoreCase(normalized) || "debug".equalsIgnoreCase(normalized)) {
            return Optional.of(CategoryInfo.fromEnum(ReportCategory.DEBUG));
        }
        Optional<CategoryInfo> builtIn = Arrays.stream(ReportCategory.values())
                .filter(c -> c != ReportCategory.ALL && normalized.equals(c.getThreadId()))
                .findFirst()
                .map(CategoryInfo::fromEnum);
        if (builtIn.isPresent()) {
            return builtIn;
        }
        return repository.findByThreadId(normalized).map(this::toInfo);
    }

    @Transactional(readOnly = true)
    public Optional<CategoryInfo> resolveByCode(String code) {
        if (code == null || code.isBlank() || "all".equalsIgnoreCase(code)) {
            return Optional.of(CategoryInfo.fromEnum(ReportCategory.ALL));
        }
        Optional<CategoryInfo> builtIn = ReportCategory.fromCode(code).map(CategoryInfo::fromEnum);
        if (builtIn.isPresent()) {
            return builtIn;
        }
        return repository.findByCodeIgnoreCase(code.trim()).map(this::toInfo);
    }

    @Transactional
    public CategoryInfo createCustom(String code, String threadId, String label, String icon) {
        String normalizedCode = normalizeCode(code);
        String normalizedThreadId = threadId.trim();
        String normalizedLabel = label.trim();
        String normalizedIcon = icon.trim();

        validateNewCategory(normalizedCode, normalizedThreadId, normalizedLabel, normalizedIcon);

        ReportCategoryEntity entity = new ReportCategoryEntity();
        entity.setCode(normalizedCode);
        entity.setThreadId(normalizedThreadId);
        entity.setLabel(normalizedLabel);
        entity.setIcon(normalizedIcon);
        entity.setSortOrder(nextSortOrder());
        return toInfo(repository.save(entity));
    }

    private void validateNewCategory(String code, String threadId, String label, String icon) {
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "Код категории: латиница в нижнем регистре, 2–31 символ (a-z, 0-9, дефис), начинается с буквы");
        }
        if (!THREAD_ID_PATTERN.matcher(threadId).matches()) {
            throw new IllegalArgumentException("message_thread_id: 1–64 символа (буквы, цифры, _, -)");
        }
        if (label.isBlank() || label.length() > 120) {
            throw new IllegalArgumentException("Название: от 1 до 120 символов");
        }
        if (!CategoryIcons.isAllowed(icon)) {
            throw new IllegalArgumentException("Выберите иконку из списка");
        }
        if (ReportCategory.fromCode(code).filter(c -> c != ReportCategory.ALL).isPresent()) {
            throw new IllegalArgumentException("Код «" + code + "» уже занят встроенной категорией");
        }
        if (ReportCategory.fromThreadId(threadId).filter(c -> c != ReportCategory.ALL).isPresent()) {
            throw new IllegalArgumentException("thread_id «" + threadId + "» уже занят встроенной категорией");
        }
        if ("local".equalsIgnoreCase(threadId) || "debug".equalsIgnoreCase(threadId)
                || "localstart".equalsIgnoreCase(threadId)) {
            throw new IllegalArgumentException("thread_id зарезервирован для раздела «Отладка»");
        }
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Категория с кодом «" + code + "» уже существует");
        }
        if (repository.existsByThreadId(threadId)) {
            throw new IllegalArgumentException("Категория с message_thread_id «" + threadId + "» уже существует");
        }
    }

    private static String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private int nextSortOrder() {
        return repository.findAllByOrderBySortOrderAscLabelAsc().stream()
                .mapToInt(ReportCategoryEntity::getSortOrder)
                .max()
                .orElse(100) + 1;
    }

    private CategoryInfo toInfo(ReportCategoryEntity entity) {
        return new CategoryInfo(entity.getThreadId(), entity.getCode(),
                entity.getLabel(), entity.getIcon());
    }
}
