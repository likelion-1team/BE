package com.babylion.team1.domain.analysis.repository;

import com.babylion.team1.domain.analysis.entity.ReworkAssessment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReworkAssessmentRepository extends JpaRepository<ReworkAssessment, Long> {
    Optional<ReworkAssessment> findFirstByItemIdOrderByVersionDesc(Long itemId);

    @Query("""
            select a from ReworkAssessment a where a.item.id in :itemIds
            and a.version = (select max(current.version) from ReworkAssessment current where current.item.id = a.item.id)
            """)
    List<ReworkAssessment> findLatestByItemIds(Collection<Long> itemIds);

    @Modifying
    @Query("""
            update ReworkAssessment a set a.note = null
            where a.item.id in (select i.id from AnalysisItem i where i.version.id = :versionId)
            """)
    void clearNotesByVersionId(Long versionId);
}
