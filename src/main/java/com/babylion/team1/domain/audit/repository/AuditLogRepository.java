package com.babylion.team1.domain.audit.repository;

import com.babylion.team1.domain.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
