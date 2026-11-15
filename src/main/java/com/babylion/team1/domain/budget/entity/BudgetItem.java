package com.babylion.team1.domain.budget.entity;

import com.babylion.team1.domain.project.entity.Wbs;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 확정 예산 */
@Entity
@Table(name = "budget_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BudgetItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "budget_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revision_id", nullable = false)
	private BudgetRevision budgetRevision;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "wbs_id")
	private Wbs wbs;

	@Column(name = "account_code", nullable = false, length = 50)
	private String accountCode;

	@Column(name = "budget_date", nullable = false)
	private LocalDate budgetDate;

	@Column(name = "budget_amount", nullable = false, precision = 18, scale = 2)
	private BigDecimal budgetAmount;

	@Builder
	private BudgetItem(BudgetRevision budgetRevision, Wbs wbs, String accountCode, LocalDate budgetDate, BigDecimal budgetAmount) {
		this.budgetRevision = budgetRevision;
		this.wbs = wbs;
		this.accountCode = accountCode;
		this.budgetDate = budgetDate;
		this.budgetAmount = budgetAmount;
	}
}
