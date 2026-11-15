package com.babylion.team1.domain.cost.entity;

import com.babylion.team1.domain.project.entity.Project;
import com.babylion.team1.domain.project.entity.Wbs;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 비용 실적 */
@Entity
@Table(name = "cost_actual")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CostActual {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "actual_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "wbs_id")
	private Wbs wbs;

	@Column(name = "account_code", nullable = false, length = 50)
	private String accountCode;

	@Column(name = "actual_amount", nullable = false, precision = 18, scale = 2)
	private BigDecimal actualAmount;

	@Column(name = "actual_date", nullable = false)
	private LocalDate actualDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "settlement_status", nullable = false, length = 20)
	private SettlementStatus settlementStatus;

	@Column(name = "synced_at", nullable = false)
	private LocalDateTime syncedAt;

	@Builder
	private CostActual(Project project, Wbs wbs, String accountCode, BigDecimal actualAmount, LocalDate actualDate, SettlementStatus settlementStatus, LocalDateTime syncedAt) {
		this.project = project;
		this.wbs = wbs;
		this.accountCode = accountCode;
		this.actualAmount = actualAmount;
		this.actualDate = actualDate;
		this.settlementStatus = settlementStatus;
		this.syncedAt = syncedAt;
	}
}
