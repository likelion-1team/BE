package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.FeedbackEvidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackEvidenceRepository extends JpaRepository<FeedbackEvidence, Long> {

    List<FeedbackEvidence> findByFeedbackIdOrderByIdAsc(Long feedbackId);

    void deleteByFeedbackId(Long feedbackId);
}
