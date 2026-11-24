package com.babylion.team1.domain.analysis.entity;

import com.babylion.team1.domain.conversation.entity.ConversationVersion;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis_items",
    uniqueConstraints = @UniqueConstraint(name = "uk_analysis_items_analysis_version",
        columnNames = {"analysis_id", "version_id"}),
    indexes = {
        @Index(name = "idx_analysis_items_team_task_date", columnList = "team_id, task_type_id, conversation_date"),
        @Index(name = "idx_analysis_items_team_user_date", columnList = "team_id, user_id, conversation_date"),
        @Index(name = "idx_analysis_items_status", columnList = "status"),
        @Index(name = "idx_analysis_items_team_current", columnList = "team_id, is_current")
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private ConversationVersion version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_type_id", nullable = false)
    private TeamTaskType taskType;

    @Column(name = "conversation_date", nullable = false)
    private LocalDate conversationDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private AnalysisItemStatus status = AnalysisItemStatus.QUEUED;

    @Builder.Default
    @ColumnDefault("0")
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scope_classification", length = 30)
    private ScopeClassification scopeClassification;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ai_outcome", length = 30)
    private AnalysisOutcome aiOutcome;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ai_rework_type", length = 30)
    private AnalysisReworkType aiReworkType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_evidence_message_keys")
    private List<String> aiEvidenceMessageKeys;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "rubric_version", length = 20)
    private String rubricVersion;

    @Column(name = "estimated_tokens")
    private Integer estimatedTokens;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "confirmed_outcome", length = 30)
    private AnalysisOutcome confirmedOutcome;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "confirmed_rework_type", length = 30)
    private AnalysisReworkType confirmedReworkType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "finished_at", columnDefinition = "DATETIME(6)")
    private Instant finishedAt;

    public void begin() {
        status = AnalysisItemStatus.RUNNING;
        attemptCount++;
    }

    public void succeed(int tokens, String model, boolean current) {
        status = AnalysisItemStatus.SUCCEEDED;
        estimatedTokens = tokens;
        modelVersion = model;
        this.current = current;
        finishedAt = Instant.now();
    }

    public boolean recordFailedAttempt() {
        attemptCount++;
        if (attemptCount < 3) {
            return false;
        }
        status = AnalysisItemStatus.FAILED;
        failureCode = "ANALYSIS_FAILED";
        finishedAt = Instant.now();
        return true;
    }

    public void retire() {
        current = false;
    }
}
