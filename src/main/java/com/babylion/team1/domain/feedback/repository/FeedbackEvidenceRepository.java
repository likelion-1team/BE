package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.FeedbackEvidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackEvidenceRepository extends JpaRepository<FeedbackEvidence, Long> {

    List<FeedbackEvidence> findByFeedbackIdOrderByIdAsc(Long feedbackId);

    List<FeedbackEvidence> findByFeedbackIdAndConversationIdAndFeedbackUserId(
            Long feedbackId, Long conversationId, Long userId);

    void deleteByFeedbackId(Long feedbackId);
}
