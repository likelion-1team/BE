package com.babylion.team1.domain.workrequest.entity;

import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.project.entity.Project;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 업무 요청 */
@Entity
@Table(name = "work_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "request_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "policy_version_id")
	private PolicyVersion policyVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "route_type", length = 30)
	private RouteType routeType;

	@Column(name = "task_type", nullable = false, length = 50)
	private String taskType;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_method", nullable = false, length = 30)
	private ProcessingMethod processingMethod;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private WorkRequestStatus status;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "is_success")
	private Boolean isSuccess;

	@Column(name = "rework_count", nullable = false)
	private Integer reworkCount;

	@Builder
	private WorkRequest(User user, Project project, PolicyVersion policyVersion, RouteType routeType, String taskType, ProcessingMethod processingMethod, WorkRequestStatus status, LocalDateTime completedAt, Boolean isSuccess, Integer reworkCount) {
		this.user = user;
		this.project = project;
		this.policyVersion = policyVersion;
		this.routeType = routeType;
		this.taskType = taskType;
		this.processingMethod = processingMethod;
		this.status = status;
		this.completedAt = completedAt;
		this.isSuccess = isSuccess;
		this.reworkCount = reworkCount;
	}
}
