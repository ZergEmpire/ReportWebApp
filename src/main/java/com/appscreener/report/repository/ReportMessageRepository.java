package com.appscreener.report.repository;

import com.appscreener.report.entity.ReportMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface ReportMessageRepository extends JpaRepository<ReportMessageEntity, String> {

    @Query("SELECT m FROM ReportMessageEntity m JOIN FETCH m.testRun")
    List<ReportMessageEntity> findAllWithTestRun();

    List<ReportMessageEntity> findByTestRun_IdOrderByReceivedAtAsc(String testRunId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ReportMessageEntity m WHERE m.receivedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
