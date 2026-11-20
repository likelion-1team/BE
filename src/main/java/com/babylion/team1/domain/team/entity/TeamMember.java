package com.babylion.team1.domain.team.entity;

import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 팀원과 역할 */
@Entity
@Table(
	name = "team_members",
	uniqueConstraints = @UniqueConstraint(name = "uk_team_members_team_user", columnNames = {"team_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "role", nullable = false, length = 20)
	private TeamRole role;

	@CreationTimestamp
	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;

	private TeamMember(Team team, User user, TeamRole role) {
		this.team = team;
		this.user = user;
		this.role = role;
	}

	/** 팀을 만든 사람을 팀장으로 등록 */
	public static TeamMember leader(Team team, User user) {
		return new TeamMember(team, user, TeamRole.LEADER);
	}

	/** 팀장이 이메일로 추가한 팀원 */
	public static TeamMember member(Team team, User user) {
		return new TeamMember(team, user, TeamRole.MEMBER);
	}

	public boolean isLeader() {
		return role == TeamRole.LEADER;
	}
}
