package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.Guide;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideRepository extends JpaRepository<Guide, Long> {
    Optional<Guide> findBySharedCaseId(Long sharedCaseId);
    void deleteBySharedCaseId(Long sharedCaseId);
}
