package com.babylion.team1.domain.team.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "team_goals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, unique = true)
    private Team team;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "success_rate_min", precision = 5, scale = 4)
    private BigDecimal successRateMin;

    @Column(name = "error_rework_rate_max", precision = 5, scale = 4)
    private BigDecimal errorReworkRateMax;

    @Column(name = "tokens_per_success_max")
    private Integer tokensPerSuccessMax;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private TeamGoal(Team team) {
        this.team = team;
    }

    public static TeamGoal createDefault(Team team) {
        return new TeamGoal(team);
    }
}
