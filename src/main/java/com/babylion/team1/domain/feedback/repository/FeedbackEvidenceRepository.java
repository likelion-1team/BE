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


    boolean existsByFeedbackId(Long feedbackId);

    @org.springframework.data.jpa.repository.Query("""
            select case when count(e) > 0 then true else false end
            from FeedbackEvidence e left join e.conversation.currentVersion currentVersion
            where e.feedback.id = :feedbackId and (
                exists (
                    select 1 from AnalysisItem item
                    where item.analysis.upload.id = e.feedback.upload.id
                        and item.version.conversation.id = e.conversation.id and item.version.deletedAt is not null
                ) or (currentVersion.deletedAt is not null and not exists (
                    select 1 from AnalysisItem item
                    where item.analysis.upload.id = e.feedback.upload.id
                        and item.version.conversation.id = e.conversation.id
                ))
            )
            """)
    boolean hasDeletedOriginal(Long feedbackId);

    List<FeedbackEvidence> findByFeedbackIdOrderByIdAsc(Long feedbackId);

    List<FeedbackEvidence> findByFeedbackIdAndConversationIdAndFeedbackUserId(
            Long feedbackId, Long conversationId, Long userId);

    void deleteByFeedbackId(Long feedbackId);
}
