package com.babylion.team1.domain.policy.entity;

import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 정책 변경 추천 */
@Entity
@Table(name = "policy_recommendation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyRecommendation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "recommendation_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "policy_id", nullable = false)
	private Policy policy;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private RecommendationStatus status;

	@Column(name = "observed_problem", columnDefinition = "text")
	private String observedProblem;

	@Column(name = "cause_candidate", columnDefinition = "text")
	private String causeCandidate;

	@Column(name = "next_action", columnDefinition = "text")
	private String nextAction;

	@Column(name = "rejection_reason", columnDefinition = "text")
	private String rejectionReason;

	@Column(name = "detail", columnDefinition = "json")
	private String detail;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decided_by")
	private User decidedBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Column(name = "sample_count", nullable = false)
	private Integer sampleCount;

	@Builder
	private PolicyRecommendation(Policy policy, RecommendationStatus status, String observedProblem, String causeCandidate, String nextAction, String rejectionReason, String detail, User decidedBy, LocalDateTime decidedAt, Integer sampleCount) {
		this.policy = policy;
		this.status = status;
		this.observedProblem = observedProblem;
		this.causeCandidate = causeCandidate;
		this.nextAction = nextAction;
		this.rejectionReason = rejectionReason;
		this.detail = detail;
		this.decidedBy = decidedBy;
		this.decidedAt = decidedAt;
		this.sampleCount = sampleCount;
	}
}
