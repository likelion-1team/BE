package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.entity.TeamGoal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamGoalRepository extends JpaRepository<TeamGoal, Long> {

    Optional<TeamGoal> findByTeamId(Long teamId);

    boolean existsByTeamId(Long teamId);

    // 매번 성공한 PUT은 값이 같더라도 버전을 한 번 올린다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update TeamGoal g
            set g.successRateMin = :successRateMin,
                g.errorReworkRateMax = :errorReworkRateMax,
                g.tokensPerSuccessMax = :tokensPerSuccessMax,
                g.updatedAt = :updatedAt,
                g.version = g.version + 1
            where g.team.id = :teamId and g.version = :expectedVersion
            """)
    int replaceIfVersionMatches(
            @Param("teamId") Long teamId,
            @Param("expectedVersion") int expectedVersion,
            @Param("successRateMin") BigDecimal successRateMin,
            @Param("errorReworkRateMax") BigDecimal errorReworkRateMax,
            @Param("tokensPerSuccessMax") Integer tokensPerSuccessMax,
            @Param("updatedAt") LocalDateTime updatedAt);
}
