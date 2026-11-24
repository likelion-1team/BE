package com.babylion.team1.domain.team.entity;

import jakarta.persistence.*;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 분석을 허용한 업무 유형과 판정 조건. 이름이나 조건이 바뀌면 새 행을 만든다. */
@Entity
@Table(
	name = "team_task_types",
	indexes = @Index(name = "idx_team_task_types_team_active", columnList = "team_id, is_active")
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
	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@JdbcTypeCode(SqlTypes.JSON)
	@ColumnDefault("(json_array())")
	@Column(name = "success_conditions", nullable = false)
	private List<String> successConditions;

	@JdbcTypeCode(SqlTypes.JSON)
	@ColumnDefault("(json_array())")
	@Column(name = "failure_conditions", nullable = false)
	private List<String> failureConditions;

	@JdbcTypeCode(SqlTypes.JSON)
	@ColumnDefault("(json_array())")
	@Column(name = "unknown_conditions", nullable = false)
	private List<String> unknownConditions;

	/** 성공 조건을 줄바꿈으로 합친 값. 분석에서 사용한다. */
	@Column(name = "success_definition", nullable = false, columnDefinition = "TEXT")
	private String successDefinition;

	/** 현재 분석 허용 여부 */
	@ColumnDefault("true")
	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@Builder
	private TeamTaskType(Team team, String name, List<String> successConditions, List<String> failureConditions,
	                     List<String> unknownConditions) {
		this.team = team;
		this.name = name;
		this.successConditions = List.copyOf(successConditions);
		this.failureConditions = List.copyOf(failureConditions);
		this.unknownConditions = List.copyOf(unknownConditions);
		this.successDefinition = String.join("\n", successConditions);
	}

	public boolean hasSameCriteria(TeamTaskType other) {
		return name.equals(other.name)
				&& successConditions.equals(other.successConditions)
				&& failureConditions.equals(other.failureConditions)
				&& unknownConditions.equals(other.unknownConditions);
	}

	public void deactivate() {
		this.isActive = false;
	}
}
