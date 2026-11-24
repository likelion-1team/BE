package com.babylion.team1.domain.feedback.entity;

import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Content;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "share_previews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharePreview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Feedback feedback;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "preview_version", nullable = false)
    private int previewVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Content content;

    @Column(name = "redaction_status", nullable = false, length = 30)
    private String redactionStatus = "PASSED";

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant expiresAt;

    @Column(name = "consumed_at", columnDefinition = "DATETIME(6)")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    public SharePreview(Feedback feedback, Content content, Instant now) {
        this.feedback = feedback;
        this.user = feedback.getUser();
        this.content = content;
        createdAt = now.truncatedTo(ChronoUnit.MICROS);
        expiresAt = createdAt.plus(30, ChronoUnit.MINUTES);
    }
}
