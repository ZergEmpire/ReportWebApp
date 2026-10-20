package com.appscreener.report.repository;

import com.appscreener.report.entity.AllureAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

public interface AllureAttachmentRepository extends JpaRepository<AllureAttachmentEntity, String> {

    @Modifying
    @Transactional
    void deleteByRunIdIn(Collection<String> runIds);
}
