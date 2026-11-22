package com.babylion.team1.domain.conversation.entity;

import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/** 직원이 고른 업무 대화(대화 1개 = 업무 1건) */
@Entity
@Table(
	name = "conversations",
	uniqueConstraints = @UniqueConstraint(name = "uk_conversations_team_external", columnNames = {"team_id", "external_id"}),
	indexes = {
		// 팀장 집계용
		@Index(name = "idx_conversations_team_task_date", columnList = "team_id, task_type_id, conversation_date"),
		// 개인 지표용
		@Index(name = "idx_conversations_team_user_date", columnList = "team_id, user_id, conversation_date")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "upload_id", nullable = false)
	private Upload upload;

	/** 대화 주인 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** 직원이 업로드 때 지정 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "task_type_id", nullable = false)
	private TeamTaskType taskType;

	/** ChatGPT 대화 ID */
	@Column(name = "external_id", nullable = false, length = 100)
	private String externalId;

	@Column(name = "title", length = 255)
	private String title;

	@Column(name = "model", length = 50)
	private String model;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	/** KST */
	@Column(name = "conversation_date", nullable = false)
	private LocalDate conversationDate;

	@Column(name = "turn_count", nullable = false)
	private Integer turnCount;

	@Column(name = "estimated_tokens", nullable = false)
	private Integer estimatedTokens;

	/** 단가 없는 모델이면 NULL */
	@Column(name = "estimated_cost", precision = 12, scale = 4)
	private BigDecimal estimatedCost;

	@Column(name = "is_success", nullable = false)
	private Boolean isSuccess;

	@ColumnDefault("false")
	@Column(name = "has_rework", nullable = false)
	private Boolean hasRework = false;

	@ColumnDefault("0")
	@Column(name = "review_time_sec", nullable = false)
	private Integer reviewTimeSec = 0;

	@ColumnDefault("0")
	@Column(name = "rework_time_sec", nullable = false)
	private Integer reworkTimeSec = 0;

	@Builder
	private Conversation(Team team, Upload upload, User user, TeamTaskType taskType, String externalId, String title,
		String model, LocalDateTime startedAt, LocalDate conversationDate, Integer turnCount, Integer estimatedTokens,
		BigDecimal estimatedCost, Boolean isSuccess, Boolean hasRework, Integer reviewTimeSec, Integer reworkTimeSec) {
		this.team = team;
		this.upload = upload;
		this.user = user;
		this.taskType = taskType;
		this.externalId = externalId;
		this.title = title;
		this.model = model;
		this.startedAt = startedAt;
		this.conversationDate = conversationDate;
		this.turnCount = turnCount;
		this.estimatedTokens = estimatedTokens;
		this.estimatedCost = estimatedCost;
		this.isSuccess = isSuccess;
		if (hasRework != null) {
			this.hasRework = hasRework;
		}
		if (reviewTimeSec != null) {
			this.reviewTimeSec = reviewTimeSec;
		}
		if (reworkTimeSec != null) {
			this.reworkTimeSec = reworkTimeSec;
		}
	}

	public void recordAnalysis(String model, int turnCount, int estimatedTokens, BigDecimal estimatedCost,
		boolean success, boolean rework, int reviewTimeSec, int reworkTimeSec) {
		this.model = model;
		this.turnCount = turnCount;
		this.estimatedTokens = estimatedTokens;
		this.estimatedCost = estimatedCost;
		this.isSuccess = success;
		this.hasRework = rework;
		this.reviewTimeSec = reviewTimeSec;
		this.reworkTimeSec = reworkTimeSec;
	}

	public boolean isOwnedBy(Long userId) {
		return user.getId().equals(userId);
	}
}
