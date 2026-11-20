package com.babylion.team1.domain.team.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/** 분석을 허용한 업무 유형과 성공 정의 */
@Entity
@Table(
	name = "team_task_types",
	uniqueConstraints = @UniqueConstraint(name = "uk_team_task_types_team_name", columnNames = {"team_id", "name"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamTaskType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	/** 업무 유형(예: 주간 보고서 작성) */
	@Column(name = "name", nullable = false, length = 50)
	private String name;

	@Column(name = "success_definition", nullable = false, length = 500)
	private String successDefinition;

	/** 현재 분석 허용 여부 */
	@ColumnDefault("true")
	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@Builder
	private TeamTaskType(Team team, String name, String successDefinition) {
		this.team = team;
		this.name = name;
		this.successDefinition = successDefinition;
	}

	public void update(String name, String successDefinition) {
		this.name = name;
		this.successDefinition = successDefinition;
	}

	public void activate() {
		this.isActive = true;
	}

	public void deactivate() {
		this.isActive = false;
	}
}
