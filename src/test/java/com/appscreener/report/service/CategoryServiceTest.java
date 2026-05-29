package com.appscreener.report.service;

import com.appscreener.report.entity.ReportCategoryEntity;
import com.appscreener.report.model.CategoryInfo;
import com.appscreener.report.repository.ReportCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private ReportCategoryRepository repository;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(repository);
    }

    @Test
    void resolveByThreadId_builtinApi() {
        Optional<CategoryInfo> cat = service.resolveByThreadId("2");
        assertThat(cat).isPresent();
        assertThat(cat.get().getCode()).isEqualTo("api");
    }

    @Test
    void resolveByThreadId_custom() {
        ReportCategoryEntity entity = new ReportCategoryEntity();
        entity.setCode("security");
        entity.setThreadId("9001");
        entity.setLabel("Security");
        entity.setIcon("🔒");
        entity.setSortOrder(101);
        when(repository.findByThreadId("9001")).thenReturn(Optional.of(entity));

        Optional<CategoryInfo> cat = service.resolveByThreadId("9001");
        assertThat(cat).isPresent();
        assertThat(cat.get().getCode()).isEqualTo("security");
        assertThat(cat.get().getLabel()).isEqualTo("Security");
    }

    @Test
    void navigable_includesCustomAfterBuiltin() {
        ReportCategoryEntity entity = new ReportCategoryEntity();
        entity.setCode("perf");
        entity.setThreadId("7777");
        entity.setLabel("Performance");
        entity.setIcon("⚡");
        entity.setSortOrder(101);
        when(repository.findAllByOrderBySortOrderAscLabelAsc()).thenReturn(List.of(entity));

        List<CategoryInfo> nav = service.navigable();
        assertThat(nav).extracting(CategoryInfo::getCode)
                .contains("all", "api", "perf", "local");
    }

    @Test
    void createCustom_rejectsBuiltinCode() {
        assertThatThrownBy(() -> service.createCustom("api", "9999", "Dup", "🧪"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("встроенной");
    }

    @Test
    void deleteCustom_removesEntity() {
        ReportCategoryEntity entity = new ReportCategoryEntity();
        entity.setId(5L);
        when(repository.findById(5L)).thenReturn(java.util.Optional.of(entity));

        service.deleteCustom(5L);

        verify(repository).delete(entity);
    }
}
