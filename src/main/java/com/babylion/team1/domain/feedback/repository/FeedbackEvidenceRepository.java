package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.FeedbackEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackEvidenceRepository extends JpaRepository<FeedbackEvidence, Long> {

    void deleteByFeedbackId(Long feedbackId);
}
