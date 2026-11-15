package com.babylion.team1.domain.workrequest.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** AI 실행 및 경로 로그 */
@Entity
@Table(name = "ai_execution_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiExecutionLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "log_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private WorkRequest workRequest;

	@Enumerated(EnumType.STRING)
	@Column(name = "execution_type", nullable = false, length = 20)
	private ExecutionType executionType;

	@Column(name = "response_time_ms")
	private Integer responseTimeMs;

	@Column(name = "confidence_score", precision = 5, scale = 2)
	private BigDecimal confidenceScore;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "model_version", length = 50)
	private String modelVersion;

	@Column(name = "is_correct")
	private Boolean isCorrect;

	@Builder
	private AiExecutionLog(WorkRequest workRequest, ExecutionType executionType, Integer responseTimeMs, BigDecimal confidenceScore, String modelVersion, Boolean isCorrect) {
		this.workRequest = workRequest;
		this.executionType = executionType;
		this.responseTimeMs = responseTimeMs;
		this.confidenceScore = confidenceScore;
		this.modelVersion = modelVersion;
		this.isCorrect = isCorrect;
	}
}
