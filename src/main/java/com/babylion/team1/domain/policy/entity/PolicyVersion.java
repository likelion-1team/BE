package com.babylion.team1.domain.policy.entity;

import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.workrequest.entity.RouteType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 정책 버전 및 이력 */
@Entity
@Table(name = "policy_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "version_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "policy_id", nullable = false)
	private Policy policy;

	@Column(name = "version_name", nullable = false, length = 50)
	private String versionName;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PolicyVersionStatus status;

	@Column(name = "confidence_threshold", precision = 5, scale = 2)
	private BigDecimal confidenceThreshold;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approved_by")
	private User approvedBy;

	@Column(name = "applied_at")
	private LocalDateTime appliedAt;

	@Column(name = "allowed_tools", nullable = false, columnDefinition = "json")
	private String allowedTools;

	@Column(name = "success_definition", columnDefinition = "text")
	private String successDefinition;

	@Column(name = "quality_time_criteria", columnDefinition = "json")
	private String qualityTimeCriteria;

	@Column(name = "exception_conditions", columnDefinition = "json")
	private String exceptionConditions;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@Column(name = "rolled_back_at")
	private LocalDateTime rolledBackAt;

	@Column(name = "rollback_reason", length = 500)
	private String rollbackReason;

	@Enumerated(EnumType.STRING)
	@Column(name = "default_route", length = 30)
	private RouteType defaultRoute;

	@Builder
	private PolicyVersion(Policy policy, String versionName, PolicyVersionStatus status, BigDecimal confidenceThreshold, User approvedBy, LocalDateTime appliedAt, String allowedTools, String successDefinition, String qualityTimeCriteria, String exceptionConditions, User createdBy, LocalDateTime rolledBackAt, String rollbackReason, RouteType defaultRoute) {
		this.policy = policy;
		this.versionName = versionName;
		this.status = status;
		this.confidenceThreshold = confidenceThreshold;
		this.approvedBy = approvedBy;
		this.appliedAt = appliedAt;
		this.allowedTools = allowedTools;
		this.successDefinition = successDefinition;
		this.qualityTimeCriteria = qualityTimeCriteria;
		this.exceptionConditions = exceptionConditions;
		this.createdBy = createdBy;
		this.rolledBackAt = rolledBackAt;
		this.rollbackReason = rollbackReason;
		this.defaultRoute = defaultRoute;
	}
}
