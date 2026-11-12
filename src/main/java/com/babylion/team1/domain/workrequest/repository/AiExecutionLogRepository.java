package com.babylion.team1.domain.workrequest.repository;

import com.babylion.team1.domain.workrequest.entity.AiExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiExecutionLogRepository extends JpaRepository<AiExecutionLog, Long> {
}
