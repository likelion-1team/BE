package com.babylion.team1.domain.feedback.entity;

import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "guides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guide {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_case_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SharedCase sharedCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "designated_by", nullable = false)
    private User designatedBy;

    @Column(name = "designated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant designatedAt;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(length = 500)
    private String note;

    public Guide(SharedCase sharedCase, User designatedBy, Instant designatedAt) {
        this.team = sharedCase.getTeam();
        this.sharedCase = sharedCase;
        this.designatedBy = designatedBy;
        this.designatedAt = designatedAt;
    }
}
