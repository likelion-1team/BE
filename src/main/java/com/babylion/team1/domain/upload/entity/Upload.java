package com.babylion.team1.domain.upload.entity;

import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.policy.ServerPolicy;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

/** 올린 파일 + 분석 상태 */
@Entity
@Table(name = "uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Upload {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "settings_id", updatable = false)
	private TeamSettings settings;

	/** 업로더 = 대화 주인 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "uploader_id", nullable = false)
	private User uploader;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	/** 대상 기간 안 대화 수 */
	@Column(name = "conversation_count", nullable = false)
	private Integer conversationCount;

	/** 직원이 고른 대화 수 */
	@ColumnDefault("0")
	@Column(name = "selected_count", nullable = false)
	private Integer selectedCount = 0;

	@ColumnDefault("0")
	@Column(name = "duplicate_count", nullable = false)
	private Integer duplicateCount = 0;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "status", nullable = false, length = 20)
	private UploadStatus status = UploadStatus.UPLOADED;

	/** 0~100 */
	@ColumnDefault("0")
	@Column(name = "progress", nullable = false)
	private Integer progress = 0;

	/** FAILED일 때만 */
	@Column(name = "fail_reason", length = 500)
	private String failReason;

	@CreationTimestamp
	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private LocalDateTime uploadedAt;

	@Column(name = "target_from", updatable = false)
	private LocalDate targetFrom;

	@Column(name = "target_to", updatable = false)
	private LocalDate targetTo;

	/** DONE 전 NULL */
	@Column(name = "analyzed_at")
	private LocalDateTime analyzedAt;

	@Builder
	private Upload(Team team, User uploader, String fileName, Integer conversationCount, Integer duplicateCount,
	               LocalDate targetFrom, LocalDate targetTo, TeamSettings settings) {
		this.team = team;
		this.settings = settings;
		this.uploader = uploader;
		this.fileName = fileName;
		this.conversationCount = conversationCount;
		this.targetFrom = targetFrom;
		this.targetTo = targetTo;
		if (duplicateCount != null) {
			this.duplicateCount = duplicateCount;
		}
	}

	@Column(name = "consumed_at")
	private LocalDateTime consumedAt;

	@ColumnDefault("false")
	@Column(name = "original_cleaned", nullable = false)
	private boolean originalCleaned;

	public void markOriginalCleaned() {
		originalCleaned = true;
	}

	public void consume() {
		status = UploadStatus.CONSUMED;
		consumedAt = LocalDateTime.now();
	}

	public enum LifecycleStatus { READY, CONSUMED, EXPIRED }

	public LifecycleStatus lifecycleStatus(LocalDateTime now) {
		return switch (status) {
			case CONSUMED, DONE, ANALYZING -> LifecycleStatus.CONSUMED;
			case UPLOADED, FAILED -> uploadedAt.plusMinutes(ServerPolicy.UPLOAD_RETENTION_MINUTES).isAfter(now)
					? LifecycleStatus.READY : LifecycleStatus.EXPIRED;
		};
	}

	public boolean isUploadedBy(Long userId) {
		return uploader.getId().equals(userId);
	}

	public void startAnalysis(int selectedCount) {
		this.selectedCount = selectedCount;
		this.status = UploadStatus.ANALYZING;
		this.progress = 0;
		this.failReason = null;
	}

	public void updateProgress(int progress) {
		this.progress = Math.max(0, Math.min(100, progress));
	}

	public void complete(int skippedDuplicateCount) {
		this.duplicateCount = skippedDuplicateCount;
		complete();
	}

	public void complete() {
		this.status = UploadStatus.DONE;
		this.progress = 100;
		this.analyzedAt = LocalDateTime.now();
	}

	public void fail(String failReason) {
		this.status = UploadStatus.FAILED;
		this.failReason = failReason;
	}
}
