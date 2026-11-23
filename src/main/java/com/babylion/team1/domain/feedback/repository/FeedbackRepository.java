package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.Feedback;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByUploadId(Long uploadId);

    /** Guide assignment and share changes use the same feedback row lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Feedback f where f.id = :id")
    Optional<Feedback> findByIdForUpdate(@Param("id") Long id);

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
