package com.babylion.team1.domain.analysis.repository;

import com.babylion.team1.domain.analysis.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
    Optional<Analysis> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
