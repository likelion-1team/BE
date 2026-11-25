package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.entity.SharePreview;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SharePreviewRepository extends JpaRepository<SharePreview, Long> {

    @Modifying
    @Query("delete from SharePreview p where p.feedback.id in :feedbackIds")
    void deleteByFeedbackIds(Collection<Long> feedbackIds);
}
