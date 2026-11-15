package com.babylion.team1.domain.budget.entity;

import com.babylion.team1.domain.project.entity.Wbs;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 차이 원인 분석 결과 */
@Entity
@Table(name = "variance_analysis")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VarianceAnalysis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "analysis_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revision_id", nullable = false)
	private BudgetRevision budgetRevision;

	/** WBS와 연결되지 않은 "프로젝트 수준 분석"이면 null */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "wbs_id")
	private Wbs wbs;

	@Column(name = "period_start", nullable = false)
	private LocalDate periodStart;

	@Column(name = "period_end", nullable = false)
	private LocalDate periodEnd;

	@Column(name = "facts", columnDefinition = "text")
	private String facts;

	@Column(name = "causes", columnDefinition = "text")
	private String causes;

	@Column(name = "check_items", columnDefinition = "text")
	private String checkItems;

	@Column(name = "evidence_ids", columnDefinition = "json")
	private String evidenceIds;

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_status", nullable = false, length = 20)
	private VerificationStatus verificationStatus;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	private VarianceAnalysis(BudgetRevision budgetRevision, Wbs wbs, LocalDate periodStart, LocalDate periodEnd, String facts, String causes, String checkItems, String evidenceIds, VerificationStatus verificationStatus) {
		this.budgetRevision = budgetRevision;
		this.wbs = wbs;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
		this.facts = facts;
		this.causes = causes;
		this.checkItems = checkItems;
		this.evidenceIds = evidenceIds;
		this.verificationStatus = verificationStatus;
	}
}
