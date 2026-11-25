package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.SharedCase;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedCaseRepository extends JpaRepository<SharedCase, Long> {
    Optional<SharedCase> findByFeedbackIdAndStatus(Long feedbackId, SharedCase.Status status);
    List<SharedCase> findByFeedbackIdInAndStatus(Collection<Long> feedbackIds, SharedCase.Status status);
}
