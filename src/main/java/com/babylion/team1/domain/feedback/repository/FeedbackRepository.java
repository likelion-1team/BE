package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.entity.ReactionType;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    String MY_FEEDBACK_SCOPE = """
            where f.team.id = :teamId and f.user.id = :userId
              and (:allTypes = true or f.type in :types)
            """;

    @Query("""
            select distinct f.feedbackDate from Feedback f
            """ + MY_FEEDBACK_SCOPE + """
              and (:fromDate is null or f.feedbackDate >= :fromDate)
              and (:toDate is null or f.feedbackDate <= :toDate)
            order by f.feedbackDate desc
            """)
    List<LocalDate> findMyFeedbackDates(Long teamId, Long userId, LocalDate fromDate,
                                        LocalDate toDate, boolean allTypes, List<String> types,
                                        Pageable pageable);

    @Query("""
            select new com.babylion.team1.domain.feedback.repository.FeedbackRepository$MyFeedbackRow(
                f.feedbackDate, f.id, f.type, f.title, f.occurrenceCount,
                f.expectedSavingTokens, f.expectedSavingCost, f.expectedSavingTurns,
                reaction.reaction, f.isShared, f.createdAt)
            from Feedback f
            left join FeedbackReaction reaction on reaction.feedback = f and reaction.user.id = :userId
            """ + MY_FEEDBACK_SCOPE + """
              and f.feedbackDate in :dates
            """)
    List<MyFeedbackRow> findMyFeedbackRows(Long teamId, Long userId, boolean allTypes,
                                           List<String> types, List<LocalDate> dates);

    record MyFeedbackRow(LocalDate date, Long feedbackId, String type, String title,
                         Integer occurrenceCount, Integer savingTokens, BigDecimal savingCost,
                         Integer savingTurns, ReactionType myReaction, Boolean isShared,
                         LocalDateTime createdAt) {
    }


    @Query("""
            select f from Feedback f
            join fetch f.taskType task
            where f.team.id = :teamId and f.isShared = true
              and (:taskTypeId is null or task.id = :taskTypeId)
              and (:type is null or f.type = :type or f.type = :alternateType)
            order by f.sharedAt desc, f.id desc
            """)
    List<Feedback> findSharedCases(@Param("teamId") Long teamId,
                                   @Param("taskTypeId") Long taskTypeId,
                                   @Param("type") String type,
                                   @Param("alternateType") String alternateType);

    @Query("""
            select f from Feedback f
            where f.team.id = :teamId and f.isShared = true and f.isGuide = true
              and (:type is null or f.type = :type or f.type = :alternateType)
            order by f.guideAddedAt desc, f.id desc
            """)
    List<Feedback> findGuides(@Param("teamId") Long teamId,
                              @Param("type") String type,
                              @Param("alternateType") String alternateType);
}
