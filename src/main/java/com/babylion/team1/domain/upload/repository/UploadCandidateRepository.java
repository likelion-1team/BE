package com.babylion.team1.domain.upload.repository;

import com.babylion.team1.domain.upload.entity.UploadCandidate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadCandidateRepository extends JpaRepository<UploadCandidate, Long> {

    List<UploadCandidate> findByUploadIdAndSelectionIdIn(Long uploadId, Collection<String> selectionIds);

    Page<UploadCandidate> findByUploadIdOrderByStartedAtDescIdAsc(Long uploadId, Pageable pageable);

    Page<UploadCandidate> findByUploadIdAndTitleContainingIgnoreCaseOrderByStartedAtDescIdAsc(
            Long uploadId, String title, Pageable pageable);

    @Query("""
            select distinct c.id from UploadCandidate c, ConversationVersion v
            where c.id in :candidateIds and v.conversation.team.id = c.upload.team.id
                and v.conversation.user.id = c.upload.uploader.id
                and v.conversation.externalId = c.externalId and v.contentHash = c.contentHash
            """)
    java.util.Set<Long> findReservedCandidateIds(Collection<Long> candidateIds);

    void deleteByUploadId(Long uploadId);

    @Modifying
    @Query("""
            delete from UploadCandidate c
            where c.upload.id in (select u.id from Upload u where u.uploadedAt <= :cutoff)
            """)
    void deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
