package com.babylion.team1.domain.analysis.entity;

import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outcome_assessments", uniqueConstraints =
        @UniqueConstraint(name = "uk_outcome_assessments_item_version", columnNames = {"item_id", "version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutcomeAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private AnalysisItem item;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private AnalysisOutcome outcome;

    @Column(length = 500)
    private String note;

    @Column(nullable = false, length = 30)
    private String source = "SELF_REPORT";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessed_by", nullable = false)
    private User assessedBy;

    @Column(name = "assessed_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant assessedAt;

    public OutcomeAssessment(AnalysisItem item, int version, AnalysisOutcome outcome, String note, Instant assessedAt) {
        this.item = item;
        this.version = version;
        this.outcome = outcome;
        this.note = note;
        this.assessedBy = item.getUser();
        this.assessedAt = assessedAt;
    }
}
