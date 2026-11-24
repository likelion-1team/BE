package com.babylion.team1.domain.upload.repository;

import com.babylion.team1.domain.upload.entity.UploadCandidate;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadCandidateRepository extends JpaRepository<UploadCandidate, Long> {

    @Modifying
    @Query("""
            delete from UploadCandidate c
            where c.upload.id in (select u.id from Upload u where u.uploadedAt <= :cutoff)
            """)
    void deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
