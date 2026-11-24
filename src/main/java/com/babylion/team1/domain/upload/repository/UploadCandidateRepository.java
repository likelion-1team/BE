package com.babylion.team1.domain.upload.repository;

import com.babylion.team1.domain.upload.entity.UploadCandidate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadCandidateRepository extends JpaRepository<UploadCandidate, Long> {

    List<UploadCandidate> findByUploadIdAndSelectionIdIn(Long uploadId, Collection<String> selectionIds);

    void deleteByUploadId(Long uploadId);

    @Modifying
    @Query("""
            delete from UploadCandidate c
            where c.upload.id in (select u.id from Upload u where u.uploadedAt <= :cutoff)
            """)
    void deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
