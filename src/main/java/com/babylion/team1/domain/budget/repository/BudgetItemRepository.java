package com.babylion.team1.domain.budget.repository;

import com.babylion.team1.domain.budget.entity.BudgetItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetItemRepository extends JpaRepository<BudgetItem, Long> {
}
