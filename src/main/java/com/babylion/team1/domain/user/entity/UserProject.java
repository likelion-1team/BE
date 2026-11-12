package com.babylion.team1.domain.user.entity;

import com.babylion.team1.domain.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프로젝트 조회 권한 (사용자-프로젝트 매핑) */
@Entity
@Table(name = "user_project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProject {

	@EmbeddedId
	private UserProjectId id;

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@MapsId("projectId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Builder
	private UserProject(User user, Project project) {
		this.id = new UserProjectId(user.getId(), project.getId());
		this.user = user;
		this.project = project;
	}
}
