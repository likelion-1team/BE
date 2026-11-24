package com.babylion.team1.domain.conversation.entity;

import com.babylion.team1.domain.upload.entity.Upload;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "conversation_versions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_conversation_versions_hash", columnNames = {"conversation_id", "content_hash"}),
    @UniqueConstraint(name = "uk_conversation_versions_input", columnNames = {"conversation_id", "input_version"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConversationVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_upload_id", nullable = false)
    private Upload sourceUpload;

    @Column(name = "input_version", nullable = false)
    private int inputVersion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant startedAt;

    @Column(name = "conversation_date", nullable = false)
    private LocalDate conversationDate;

    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    @Column(name = "estimated_tokens", nullable = false)
    private int estimatedTokens;

    @Column(name = "deleted_at", columnDefinition = "DATETIME(6)")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    public void recordEstimatedTokens(int tokens) {
        estimatedTokens = tokens;
    }
}
