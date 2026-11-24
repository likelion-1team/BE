package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.FeedbackEvidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackEvidenceRepository extends JpaRepository<FeedbackEvidence, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select distinct e.conversation.id as conversationId, e.feedback.id as feedbackId
            from FeedbackEvidence e
            where e.feedback.upload.id = :uploadId and e.feedback.user.id = :userId
                and e.conversation.id in :conversationIds
            order by e.feedback.id
            """)
    List<ItemFeedback> findItemFeedback(Long uploadId, Long userId, java.util.Collection<Long> conversationIds);

    interface ItemFeedback {
        Long getConversationId();
        Long getFeedbackId();
    }


    List<FeedbackEvidence> findByFeedbackIdOrderByIdAsc(Long feedbackId);

    List<FeedbackEvidence> findByFeedbackIdAndConversationIdAndFeedbackUserId(
            Long feedbackId, Long conversationId, Long userId);

    void deleteByFeedbackId(Long feedbackId);
}
