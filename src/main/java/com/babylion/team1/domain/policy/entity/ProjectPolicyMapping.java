package com.babylion.team1.domain.policy.entity;

import com.babylion.team1.domain.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프로젝트-정책 매핑 */
@Entity
@Table(name = "project_policy_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectPolicyMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "mapping_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "version_id", nullable = false)
	private PolicyVersion policyVersion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Builder
	private ProjectPolicyMapping(PolicyVersion policyVersion, Project project) {
		this.policyVersion = policyVersion;
		this.project = project;
	}
}
