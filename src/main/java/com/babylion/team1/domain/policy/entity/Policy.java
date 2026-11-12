package com.babylion.team1.domain.policy.entity;

import com.babylion.team1.domain.company.entity.Company;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정책 기본 정보 */
@Entity
@Table(name = "policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "policy_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "company_id", nullable = false)
	private Company company;

	@Column(name = "task_type", nullable = false, length = 50)
	private String taskType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@Builder
	private Policy(Company company, String taskType, User createdBy) {
		this.company = company;
		this.taskType = taskType;
		this.createdBy = createdBy;
	}
}
