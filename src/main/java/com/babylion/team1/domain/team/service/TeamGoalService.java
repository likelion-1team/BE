package com.babylion.team1.domain.team.service;

import com.babylion.team1.domain.team.dto.request.TeamGoalUpdateRequest;
import com.babylion.team1.domain.team.dto.response.TeamGoalResponse;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamGoal;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.repository.TeamGoalRepository;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamGoalService {

    private final TeamGoalRepository teamGoalRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;

    @Transactional(readOnly = true)
    public TeamGoalResponse getGoals(Long teamId, Long userId) {
        findMembership(teamId, userId);
        return readGoals(teamId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TeamGoalResponse updateGoals(Long teamId, Long userId, TeamGoalUpdateRequest request) {
        if (!findMembership(teamId, userId).isLeader()) {
            throw new CustomException(ErrorCode.ROLE_FORBIDDEN);
        }

        int expectedVersion = request.expectedVersion().intValueExact();
        if (expectedVersion == 0 && !teamGoalRepository.existsByTeamId(teamId)) {
            initializeGoalsForExistingTeam(teamId);
        }

        BigDecimal tokens = request.tokensPerSuccessMax();
        int updated = teamGoalRepository.replaceIfVersionMatches(
                teamId, expectedVersion, roundRate(request.successRateMin()), roundRate(request.errorReworkRateMax()),
                tokens == null ? null : tokens.intValueExact(),
                LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        TeamGoalResponse current = readGoals(teamId);
        if (updated == 0) {
            throw CustomException.withErrors(ErrorCode.VERSION_CONFLICT, Map.of("currentVersion", current.version()));
        }
        return current;
    }

    private TeamMember findMembership(Long teamId, Long userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private TeamGoalResponse readGoals(Long teamId) {
        return teamGoalRepository.findByTeamId(teamId)
                .map(TeamGoalResponse::from)
                .orElseGet(TeamGoalResponse::unset);
    }

    private void initializeGoalsForExistingTeam(Long teamId) {
        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!teamGoalRepository.existsByTeamId(teamId)) {
            teamGoalRepository.save(TeamGoal.createDefault(team));
        }
    }

    private BigDecimal roundRate(BigDecimal rate) {
        return rate == null ? null : rate.setScale(4, RoundingMode.HALF_UP);
    }
}
