package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.Feedback;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByUploadId(Long uploadId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Feedback f where f.id = :feedbackId")
    Optional<Feedback> findByIdForUpdate(@Param("feedbackId") Long feedbackId);

    String VERIFIED_FEEDBACK = """
            from feedbacks f
            join feedback_evidences e on e.feedback_id = f.id
            join conversations c on c.id = e.conversation_id
            left join analyses a on a.upload_id = f.upload_id
            left join conversation_versions v on v.conversation_id = c.id and exists (
                select 1 from analysis_items i where i.analysis_id = a.id and i.version_id = v.id)
            left join analysis_items i on i.analysis_id = a.id and i.version_id = v.id
            left join conversation_versions current_v on current_v.id = c.current_version_id
            join messages m on m.conversation_id = c.id and m.version_id <=> v.id
                and json_contains(e.turn_indexes, cast(m.idx as json))
            left join feedback_reactions r on r.feedback_id = f.id and r.user_id = f.user_id
            where f.team_id = :teamId and f.user_id = :userId
              and (:type is null or f.type = :type or f.type = :alternateType)
              and (case when v.id is null then current_v.deleted_at else v.deleted_at end) is null
              and (:fromDate is null or coalesce(v.conversation_date, c.conversation_date) >= :fromDate)
              and (:toDate is null or coalesce(v.conversation_date, c.conversation_date) <= :toDate)
            """;

    @Query(value = """
            select f.id as feedbackId, min(i.id) as itemId, min(c.id) as conversationId,
                f.type as type, f.title as title, count(distinct m.id) as occurrenceCount,
                f.created_at as createdAt, r.reaction as reaction,
                case when exists (select 1 from shared_cases sc where sc.feedback_id = f.id and sc.status = 'ACTIVE')
                    then 'SHARED' else 'NOT_SHARED' end as sharingStatus
            """ + VERIFIED_FEEDBACK + """
            group by f.id, f.type, f.title, f.created_at, r.reaction
            order by case when :frequency then count(distinct m.id) else 0 end desc, f.created_at desc, f.id desc
            """, countQuery = "select count(distinct f.id) " + VERIFIED_FEEDBACK
                    + " and (:frequency = true or :frequency = false)", nativeQuery = true)
    Page<MyFeedbackRow> findVerifiedFeedback(Long teamId, Long userId,
            LocalDate fromDate, LocalDate toDate, String type, String alternateType, boolean frequency, Pageable pageable);

    interface MyFeedbackRow {
        Long getFeedbackId();
        Long getItemId();
        Long getConversationId();
        String getType();
        String getTitle();
        long getOccurrenceCount();
        LocalDateTime getCreatedAt();
        String getReaction();
        String getSharingStatus();
    }
}
