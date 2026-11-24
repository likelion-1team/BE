package com.babylion.team1.domain.analysis.repository;

import com.babylion.team1.domain.analysis.entity.AnalysisItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisItemRepository extends JpaRepository<AnalysisItem, Long> {
}
