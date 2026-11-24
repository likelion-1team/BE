package com.babylion.team1.domain.analysis.repository;

import com.babylion.team1.domain.analysis.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
    java.util.List<Analysis> findTop20ByStatusInOrderByIdAsc(
            java.util.Collection<com.babylion.team1.domain.analysis.entity.AnalysisStatus> statuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Analysis a where a.id = :id")
    Optional<Analysis> lockById(Long id);

    Optional<Analysis> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
