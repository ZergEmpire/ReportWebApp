package com.appscreener.report.repository;

import com.appscreener.report.entity.AllureTestResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface AllureTestResultRepository extends JpaRepository<AllureTestResultEntity, String> {

    List<AllureTestResultEntity> findByRunIdOrderBySortIndexAsc(String runId);

    boolean existsByRunId(String runId);

    @Modifying
    @Transactional
    void deleteByRunIdIn(Collection<String> runIds);
}
