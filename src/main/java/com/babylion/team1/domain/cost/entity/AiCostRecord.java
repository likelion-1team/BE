package com.babylion.team1.domain.cost.entity;

import com.babylion.team1.domain.project.entity.Project;
import com.babylion.team1.domain.workrequest.entity.WorkRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AI 비용 기록 */
@Entity
@Table(name = "ai_cost_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiCostRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "cost_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private WorkRequest workRequest;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Enumerated(EnumType.STRING)
	@Column(name = "cost_type", nullable = false, length = 20)
	private AiCostType costType;

	@Enumerated(EnumType.STRING)
	@Column(name = "attribution_type", nullable = false, length = 20)
	private AttributionType attributionType;

	@Enumerated(EnumType.STRING)
	@Column(name = "measure_type", nullable = false, length = 20)
	private MeasureType measureType;

	@Column(name = "amount", nullable = false, precision = 18, scale = 4)
	private BigDecimal amount;

	@Column(name = "incurred_at", nullable = false)
	private LocalDateTime incurredAt;

	@Builder
	private AiCostRecord(WorkRequest workRequest, Project project, AiCostType costType, AttributionType attributionType, MeasureType measureType, BigDecimal amount, LocalDateTime incurredAt) {
		this.workRequest = workRequest;
		this.project = project;
		this.costType = costType;
		this.attributionType = attributionType;
		this.measureType = measureType;
		this.amount = amount;
		this.incurredAt = incurredAt;
	}
}
