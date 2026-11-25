package com.babylion.team1.domain.feedback.entity;

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
@Table(name = "feedback_usage_events", uniqueConstraints =
        @UniqueConstraint(name = "uk_feedback_usage_user_event", columnNames = {"user_id", "event_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedbackUsageEvent {
    public enum Type { COPY_SUCCEEDED, USE_CONFIRMED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Feedback feedback;

    @Column(name = "event_id", nullable = false, columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 30)
    private Type eventType;

    @Column(name = "recorded_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant recordedAt;

    public FeedbackUsageEvent(Feedback feedback, String eventId, Type eventType) {
        this.user = feedback.getUser();
        this.feedback = feedback;
        this.eventId = eventId;
        this.eventType = eventType;
        this.recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
