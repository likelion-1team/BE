package com.babylion.team1.domain.analysis.repository;

import com.babylion.team1.domain.analysis.entity.OutcomeAssessment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutcomeAssessmentRepository extends JpaRepository<OutcomeAssessment, Long> {
    Optional<OutcomeAssessment> findFirstByItemIdOrderByVersionDesc(Long itemId);

    @Query("""
            select a from OutcomeAssessment a where a.item.id in :itemIds
            and a.version = (select max(current.version) from OutcomeAssessment current where current.item.id = a.item.id)
            """)
    List<OutcomeAssessment> findLatestByItemIds(Collection<Long> itemIds);
}
