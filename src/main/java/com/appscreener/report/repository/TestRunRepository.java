package com.appscreener.report.repository;

import com.appscreener.report.entity.TestRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TestRunRepository extends JpaRepository<TestRunEntity, String> {

    List<TestRunEntity> findByCategoryCodeOrderByReceivedAtDesc(String categoryCode);

    List<TestRunEntity> findAllByOrderByReceivedAtDesc();

    List<TestRunEntity> findByCategoryCodeNotOrderByReceivedAtDesc(String categoryCode);

    Optional<TestRunEntity> findFirstByCategoryCodeAndReceivedAtAfterOrderByReceivedAtDesc(
            String categoryCode, Instant after);

    @Modifying
    @Transactional
    @Query("UPDATE TestRunEntity t SET t.pinned = false WHERE t.reportType IN ('TEST_RUN_SUMMARY', 'DAILY_SUMMARY')")
    void unpinAllSummaries();

    @Modifying
    @Transactional
    @Query("DELETE FROM TestRunEntity t WHERE t.receivedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
