package com.babylion.team1.domain.budget.repository;

import com.babylion.team1.domain.budget.entity.BudgetRevision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRevisionRepository extends JpaRepository<BudgetRevision, Long> {
}
