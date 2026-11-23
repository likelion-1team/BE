package com.babylion.team1.domain.feedback.entity;

import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 개인 피드백 + 공유·가이드 여부 */
@Entity
@Table(
	name = "feedbacks",
	indexes = @Index(name = "idx_feedbacks_team_user_date", columnList = "team_id, user_id, feedback_date"),
	// 가이드는 공유 허용된 피드백만 지정 가능
	check = @CheckConstraint(name = "ck_feedbacks_guide_requires_shared", constraint = "is_guide = false OR is_shared = true")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "upload_id", nullable = false)
	private Upload upload;

	/** 피드백 대상 직원 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	/** 팀장 집계·공유 사례 분류용 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "task_type_id", nullable = false)
	private TeamTaskType taskType;

	@Column(name = "feedback_date", nullable = false)
	private LocalDate feedbackDate;

	/** 피드백 유형(감지 규칙 목록 확정 전이라 문자열로 저장) */
	@Column(name = "type", nullable = false, length = 50)
	private String type;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "problem", nullable = false, columnDefinition = "TEXT")
	private String problem;

	/** 원인 후보. 없으면 [] */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "causes", nullable = false)
	private List<String> causes = new ArrayList<>();

	@Column(name = "improvement", nullable = false, columnDefinition = "TEXT")
	private String improvement;

	/** 개선 전 프롬프트 */
	@Column(name = "example_before", columnDefinition = "TEXT")
	private String exampleBefore;

	/** 개선 후 프롬프트 */
	@Column(name = "example_after", columnDefinition = "TEXT")
	private String exampleAfter;

	@Column(name = "occurrence_count", nullable = false)
	private Integer occurrenceCount;

	@ColumnDefault("0")
	@Column(name = "expected_saving_tokens", nullable = false)
	private Integer expectedSavingTokens = 0;

	@ColumnDefault("0")
	@Column(name = "expected_saving_cost", nullable = false, precision = 12, scale = 2)
	private BigDecimal expectedSavingCost = BigDecimal.ZERO;

	@ColumnDefault("0")
	@Column(name = "expected_saving_turns", nullable = false)
	private Integer expectedSavingTurns = 0;

	/** 직원의 공유 허용 */
	@ColumnDefault("false")
	@Column(name = "is_shared", nullable = false)
	private Boolean isShared = false;

	/** is_shared = false면 NULL */
	@Column(name = "shared_at")
	private LocalDateTime sharedAt;

	/** 팀장의 가이드 지정. is_shared = true일 때만 true 가능 */
	@ColumnDefault("false")
	@Column(name = "is_guide", nullable = false)
	private Boolean isGuide = false;

	@Column(name = "guide_added_at")
	private LocalDateTime guideAddedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	private Feedback(Team team, Upload upload, User user, TeamTaskType taskType, LocalDate feedbackDate, String type,
		String title, String problem, List<String> causes, String improvement, String exampleBefore,
		String exampleAfter, Integer occurrenceCount, Integer expectedSavingTokens, BigDecimal expectedSavingCost,
		Integer expectedSavingTurns) {
		this.team = team;
		this.upload = upload;
		this.user = user;
		this.taskType = taskType;
		this.feedbackDate = feedbackDate;
		this.type = type;
		this.title = title;
		this.problem = problem;
		if (causes != null) {
			this.causes = new ArrayList<>(causes);
		}
		this.improvement = improvement;
		this.exampleBefore = exampleBefore;
		this.exampleAfter = exampleAfter;
		this.occurrenceCount = occurrenceCount;
		if (expectedSavingTokens != null) {
			this.expectedSavingTokens = expectedSavingTokens;
		}
		if (expectedSavingCost != null) {
			this.expectedSavingCost = expectedSavingCost;
		}
		if (expectedSavingTurns != null) {
			this.expectedSavingTurns = expectedSavingTurns;
		}
	}

	public boolean isOwnedBy(Long userId) {
		return user.getId().equals(userId);
	}

	/** 직원이 팀에 공유 허용 */
	public void share() {
		if (Boolean.TRUE.equals(isShared)) {
			return;
		}
		this.isShared = true;
		this.sharedAt = LocalDateTime.now(KST);
	}

	/** 공유 해제. 가이드에서도 자동 해제 */
	public void unshare() {
		this.isShared = false;
		this.sharedAt = null;
		removeFromGuide();
	}

	/** 팀장이 가이드로 지정. 공유 허용된 사례만 가능 */
	public void addToGuide() {
		if (!Boolean.TRUE.equals(isShared)) {
			throw new CustomException(ErrorCode.FEEDBACK_NOT_SHARED);
		}
		if (Boolean.TRUE.equals(isGuide)) {
			return;
		}
		this.isGuide = true;
		this.guideAddedAt = LocalDateTime.now(KST).truncatedTo(ChronoUnit.MICROS);
	}

	public OffsetDateTime guideAddedAtInKst() {
		return guideAddedAt == null ? null : guideAddedAt.atZone(KST).toOffsetDateTime();
	}

	public void removeFromGuide() {
		this.isGuide = false;
		this.guideAddedAt = null;
	}
}
