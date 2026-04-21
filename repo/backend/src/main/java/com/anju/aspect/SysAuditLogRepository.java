package com.anju.aspect;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SysAuditLogRepository extends JpaRepository<SysAuditLog, Long> {

    Page<SysAuditLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<SysAuditLog> findByModuleAndCreatedAtBetween(String module, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<SysAuditLog> findByModule(String module, Pageable pageable);
}
