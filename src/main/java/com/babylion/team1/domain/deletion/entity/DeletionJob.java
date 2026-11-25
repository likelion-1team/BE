package com.babylion.team1.domain.deletion.entity;

import com.babylion.team1.domain.conversation.entity.ConversationVersion;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 원문 삭제 작업. 원문 내용은 담지 않는다. 대화 버전 하나에 작업은 하나만 만든다 */
@Entity
@Table(name = "deletion_jobs", indexes = {
    @Index(name = "idx_deletion_jobs_version_status", columnList = "version_id, status"),
    // 멈춘 작업 재처리용
    @Index(name = "idx_deletion_jobs_status_requested", columnList = "status, requested_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeletionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private ConversationVersion version;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private DeletionJobStatus status = DeletionJobStatus.PENDING;

    /** FAILED일 때만 */
    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "requested_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant requestedAt;

    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private Instant completedAt;

    public DeletionJob(ConversationVersion version, User user, Instant now) {
        this.version = version;
        this.user = user;
        this.requestedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isCompleted() {
        return status == DeletionJobStatus.COMPLETED;
    }

    /** 실패한 작업을 다시 대기시킨다. 실패 상태가 아니면 false */
    public boolean requeueIfFailed() {
        if (status != DeletionJobStatus.FAILED) {
            return false;
        }
        status = DeletionJobStatus.PENDING;
        failureCode = null;
        return true;
    }

    public void start() {
        status = DeletionJobStatus.RUNNING;
        failureCode = null;
    }

    public void complete(Instant now) {
        status = DeletionJobStatus.COMPLETED;
        failureCode = null;
        completedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void fail(String code) {
        status = DeletionJobStatus.FAILED;
        failureCode = code;
    }
}
