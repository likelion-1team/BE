package com.babylion.team1.domain.analysis.repository;

import com.babylion.team1.domain.analysis.entity.AnalysisItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AnalysisItemRepository extends JpaRepository<AnalysisItem, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select i from AnalysisItem i join fetch i.version v
            where i.analysis.upload.id = :uploadId and v.conversation.id = :conversationId
            """)
    java.util.Optional<AnalysisItem> findForFeedback(Long uploadId, Long conversationId);

    java.util.Optional<AnalysisItem> findByIdAndUserIdAndVersionDeletedAtIsNull(Long id, Long userId);

    @EntityGraph(attributePaths = {"version", "version.conversation"})
    Page<AnalysisItem> findByAnalysisIdAndUserIdAndVersionDeletedAtIsNullOrderByIdAsc(
            Long analysisId, Long userId, Pageable pageable);

    java.util.Optional<AnalysisItem> findFirstByAnalysisIdAndStatusOrderByIdAsc(
            Long analysisId, com.babylion.team1.domain.analysis.entity.AnalysisItemStatus status);

    java.util.List<AnalysisItem> findByAnalysisIdAndStatusAndClaimExpiresAtBefore(Long analysisId,
            com.babylion.team1.domain.analysis.entity.AnalysisItemStatus status, java.time.Instant cutoff);

    java.util.List<AnalysisItem> findByVersionIdAndCurrentTrue(Long versionId);
    boolean existsByVersionIdAndStatus(Long versionId,
            com.babylion.team1.domain.analysis.entity.AnalysisItemStatus status);

    boolean existsByVersionIdAndStatusNot(Long versionId,
            com.babylion.team1.domain.analysis.entity.AnalysisItemStatus status);
}
