package com.babylion.team1.domain.upload.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "upload_candidates", uniqueConstraints =
        @UniqueConstraint(name = "uk_upload_candidates_selection", columnNames = {"upload_id", "selection_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id", nullable = false)
    private Upload upload;

    @Column(name = "selection_id", nullable = false, length = 40)
    private String selectionId;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(length = 255)
    private String title;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "content_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "duplicate_type", nullable = false, length = 30)
    private DuplicateType duplicateType;

    @Column(nullable = false)
    private boolean selectable;

    @Column(name = "validation_code", length = 50)
    private String validationCode;

    public UploadCandidate(Upload upload, String externalId, String title, LocalDateTime startedAt,
                           String contentHash, DuplicateType duplicateType, String validationCode) {
        this.upload = upload;
        this.selectionId = UUID.randomUUID().toString();
        this.externalId = externalId;
        this.title = title;
        this.startedAt = startedAt;
        this.contentHash = contentHash;
        this.duplicateType = duplicateType;
        this.validationCode = validationCode;
        this.selectable = validationCode == null && duplicateType != DuplicateType.SAME_CONTENT;
    }

    public enum DuplicateType {
        NONE, SAME_CONTENT, CONTENT_UPDATED
    }
}
