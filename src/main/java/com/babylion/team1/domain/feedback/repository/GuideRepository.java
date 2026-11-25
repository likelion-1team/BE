package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.Guide;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GuideRepository extends JpaRepository<Guide, Long> {
    Optional<Guide> findBySharedCaseId(Long sharedCaseId);
    void deleteBySharedCaseId(Long sharedCaseId);

    @Modifying
    @Query("""
            delete from Guide g where g.sharedCase.id in (
                select sharedCase.id from SharedCase sharedCase where sharedCase.feedback.id in :feedbackIds
            )
            """)
    void deleteByFeedbackIds(Collection<Long> feedbackIds);
}
