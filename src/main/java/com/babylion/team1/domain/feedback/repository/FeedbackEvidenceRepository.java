package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.FeedbackEvidence;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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

    String DELETED_ORIGINAL = """
            (
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
            """;

    @org.springframework.data.jpa.repository.Query("""
            select case when count(e) > 0 then true else false end
            from FeedbackEvidence e left join e.conversation.currentVersion currentVersion
            where e.feedback.id = :feedbackId and
            """ + DELETED_ORIGINAL)
    boolean hasDeletedOriginal(Long feedbackId);

    @org.springframework.data.jpa.repository.Query("""
            select m.content from FeedbackEvidence e, Message m
            where e.feedback.id = :feedbackId and m.conversation.id = e.conversation.id
                and (m.version is null or exists (
                    select 1 from AnalysisItem item
                    where item.analysis.upload.id = e.feedback.upload.id and item.version.id = m.version.id
                ))
            """)
    List<String> findOriginalTexts(Long feedbackId);

    List<FeedbackEvidence> findByFeedbackIdOrderByIdAsc(Long feedbackId);

    List<FeedbackEvidence> findByFeedbackIdAndConversationIdAndFeedbackUserId(
            Long feedbackId, Long conversationId, Long userId);

    void deleteByFeedbackId(Long feedbackId);

    /** 이 버전을 분석해 만든 피드백. 피드백의 업로드가 이 버전을 분석했고 근거 대화가 이 버전의 대화여야 한다 */
    @Query("""
            select distinct e.feedback.id from FeedbackEvidence e
            where exists (
                select 1 from AnalysisItem item
                where item.version.id = :versionId and item.analysis.upload.id = e.feedback.upload.id
                    and item.version.conversation.id = e.conversation.id
            )
            order by e.feedback.id
            """)
    List<Long> findFeedbackIdsByVersionId(Long versionId);

    @Modifying
    @Query("delete from FeedbackEvidence e where e.feedback.id in :feedbackIds")
    void deleteByFeedbackIds(Collection<Long> feedbackIds);
}
