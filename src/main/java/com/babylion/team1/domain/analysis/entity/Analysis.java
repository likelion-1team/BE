package com.babylion.team1.domain.analysis.entity;

import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
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
@Table(name = "analyses", uniqueConstraints = {
    @UniqueConstraint(name = "uk_analyses_upload", columnNames = "upload_id"),
    @UniqueConstraint(name = "uk_analyses_user_idempotency", columnNames = {"user_id", "idempotency_key"})
}, indexes = @Index(name = "idx_analyses_status", columnList = "status"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_id", nullable = false)
    private Upload upload;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settings_id", nullable = false)
    private TeamSettings settings;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String requestHash;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private AnalysisStatus status = AnalysisStatus.QUEUED;

    @Column(name = "stage", length = 30)
    private String stage;

    @Column(name = "selected_count", nullable = false)
    private int selectedCount;

    @Builder.Default
    @ColumnDefault("0")
    @Column(name = "completed_count", nullable = false)
    private int completedCount = 0;

    @Builder.Default
    @ColumnDefault("0")
    @Column(name = "succeeded_count", nullable = false)
    private int succeededCount = 0;

    @Builder.Default
    @ColumnDefault("0")
    @Column(name = "failed_count", nullable = false)
    private int failedCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "started_at", columnDefinition = "DATETIME(6)")
    private Instant startedAt;

    @Column(name = "finished_at", columnDefinition = "DATETIME(6)")
    private Instant finishedAt;
}
