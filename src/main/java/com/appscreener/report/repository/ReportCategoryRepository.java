package com.appscreener.report.repository;

import com.appscreener.report.entity.ReportCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportCategoryRepository extends JpaRepository<ReportCategoryEntity, Long> {

    List<ReportCategoryEntity> findAllByOrderBySortOrderAscLabelAsc();

    Optional<ReportCategoryEntity> findByCodeIgnoreCase(String code);

    Optional<ReportCategoryEntity> findByThreadId(String threadId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByThreadId(String threadId);
}
