package com.babylion.team1.domain.team.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/** 분석 대상 기간·온보딩 시작일·기준값 (teams와 1:1, 팀 생성 시 기본값으로 생성) */
@Entity
@Table(name = "team_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamSettings {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 표본 부족 기준 기본값(하루 최소 대화 수). 팀 결정 전 임시값 */
	public static final int DEFAULT_MIN_DAILY_CONVERSATIONS = 3;
	/** 시간당 인건비 기본값(원). 팀 결정 전 임시값 */
	public static final int DEFAULT_HOURLY_LABOR_COST = 30000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false, unique = true)
	private Team team;

	/** 분석 대상 기간. 팀장이 설정하기 전 NULL */
	@Column(name = "target_from")
	private LocalDate targetFrom;

	@Column(name = "target_to")
	private LocalDate targetTo;

	/** 전/후 비교 기준일. 미설정이면 비교 카드 숨김 */
	@Column(name = "onboarding_start_date")
	private LocalDate onboardingStartDate;

	/** 대상 건수(선택 입력) */
	@Column(name = "target_count")
	private Integer targetCount;

	/** 표본 부족 기준 */
	@ColumnDefault("3")
	@Column(name = "min_daily_conversations", nullable = false)
	private Integer minDailyConversations = DEFAULT_MIN_DAILY_CONVERSATIONS;

	/** 시간당 인건비(원) */
	@ColumnDefault("30000")
	@Column(name = "hourly_labor_cost", nullable = false)
	private Integer hourlyLaborCost = DEFAULT_HOURLY_LABOR_COST;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	private TeamSettings(Team team) {
		this.team = team;
		this.updatedAt = nowInKst();
	}

	/** 팀 생성 시 기본값으로 생성 */
	public static TeamSettings createDefault(Team team) {
		return new TeamSettings(team);
	}

	public void updateTarget(LocalDate targetFrom, LocalDate targetTo, LocalDate onboardingStartDate, Integer targetCount) {
		this.targetFrom = targetFrom;
		this.targetTo = targetTo;
		this.onboardingStartDate = onboardingStartDate;
		this.targetCount = targetCount;
		this.updatedAt = nowInKst();
	}

	public OffsetDateTime updatedAtInKst() {
		return updatedAt.atZone(KST).toOffsetDateTime();
	}

	private static LocalDateTime nowInKst() {
		return LocalDateTime.now(KST).truncatedTo(ChronoUnit.MICROS);
	}

	public boolean isTargetSet() {
		return targetFrom != null && targetTo != null;
	}
}
