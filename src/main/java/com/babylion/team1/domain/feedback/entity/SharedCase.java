package com.babylion.team1.domain.feedback.entity;

import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Content;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shared_cases", indexes = @Index(name = "idx_shared_cases_team_status", columnList = "team_id,status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Feedback feedback;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_type_id", nullable = false)
    private TeamTaskType taskType;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preview_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SharePreview preview;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Content content;

    public enum Status { ACTIVE, REVOKED, DELETED }

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private Status status = Status.ACTIVE;

    @Column(name = "shared_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant sharedAt;

    @Column(name = "revoked_at", columnDefinition = "DATETIME(6)")
    private Instant revokedAt;

    public SharedCase(Feedback feedback, SharePreview preview, Instant now) {
        this.feedback = feedback;
        this.team = feedback.getTeam();
        this.taskType = feedback.getTaskType();
        this.preview = preview;
        this.content = preview.getContent();
        this.sharedAt = now;
    }

    public void revoke(Instant now) {
        status = Status.REVOKED;
        revokedAt = now;
    }
}
