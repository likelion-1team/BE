package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.FeedbackReaction;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FeedbackReactionRepository extends JpaRepository<FeedbackReaction, Long> {

    Optional<FeedbackReaction> findByFeedbackIdAndUserId(Long feedbackId, Long userId);

    void deleteByFeedbackId(Long feedbackId);

    @Modifying
    @Query("delete from FeedbackReaction e where e.feedback.id in :feedbackIds")
    void deleteByFeedbackIds(Collection<Long> feedbackIds);
}
