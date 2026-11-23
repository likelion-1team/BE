package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.FeedbackReaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackReactionRepository extends JpaRepository<FeedbackReaction, Long> {

    Optional<FeedbackReaction> findByFeedbackIdAndUserId(Long feedbackId, Long userId);

    void deleteByFeedbackId(Long feedbackId);
}
