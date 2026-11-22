package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    java.util.List<Feedback> findByUploadId(Long uploadId);
}
