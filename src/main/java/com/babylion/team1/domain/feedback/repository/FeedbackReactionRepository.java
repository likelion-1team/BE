package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.FeedbackReaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackReactionRepository extends JpaRepository<FeedbackReaction, Long> {
}
