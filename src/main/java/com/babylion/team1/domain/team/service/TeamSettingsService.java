package com.babylion.team1.domain.team.service;

import com.babylion.team1.domain.team.dto.request.TeamSettingsUpdateRequest;
import com.babylion.team1.domain.team.dto.response.TeamSettingsResponse;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.team.repository.TeamTaskTypeRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.global.response.FieldErrorResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamSettingsService {

    private static final int MAX_TARGET_DAYS = 366;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamSettingsRepository teamSettingsRepository;
    private final TeamTaskTypeRepository teamTaskTypeRepository;

    @Transactional(readOnly = true)
    public TeamSettingsResponse getSettings(Long teamId, Long userId) {
        findMembership(teamId, userId);
        return teamSettingsRepository.findByTeamId(teamId)
                .map(TeamSettingsResponse::from)
                .orElseGet(TeamSettingsResponse::unset);
    }

    @Transactional
    public TeamSettingsResponse updateSettings(Long teamId, Long userId, TeamSettingsUpdateRequest request) {
        // 같은 팀의 저장을 직렬화한다. 잠금을 얻은 뒤에 읽어야 다른 요청이 저장한 최신 버전이 보인다.
        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        TeamMember requester = findMembership(teamId, userId);
        if (!requester.isLeader()) {
            throw new CustomException(ErrorCode.ROLE_FORBIDDEN);
        }

        LocalDate targetFrom = parseDate("targetFrom", request.targetFrom());
        LocalDate targetTo = parseDate("targetTo", request.targetTo());
        LocalDate onboardingStartDate = request.onboardingStartDate() == null ? null
                : parseDate("onboardingStartDate", request.onboardingStartDate());
        validatePeriod(targetFrom, targetTo, onboardingStartDate);

        TeamSettings current = teamSettingsRepository.findByTeamId(teamId)
                .orElseGet(() -> TeamSettings.createDefault(team));
        if (current.getVersion() != request.expectedVersion().intValueExact()) {
            throw CustomException.withErrors(ErrorCode.VERSION_CONFLICT,
                    Map.of("currentVersion", current.getVersion()));
        }

        TeamTaskType taskType = resolveTaskType(team, current.getTaskType(), request.taskType());
        Integer targetCount = request.targetCount() == null ? null : request.targetCount().intValueExact();
        TeamSettings next = current.nextVersion(taskType, targetFrom, targetTo, onboardingStartDate, targetCount,
                requester.getUser());
        return TeamSettingsResponse.from(teamSettingsRepository.save(next));
    }

    /** 이름이나 조건이 바뀌면 이전 버전이 참조하는 업무 유형은 그대로 두고 새로 만든다. */
    private TeamTaskType resolveTaskType(Team team, TeamTaskType current, TeamSettingsUpdateRequest.TaskType request) {
        TeamTaskType requested = TeamTaskType.builder()
                .team(team)
                .name(request.name())
                .successConditions(request.successConditions())
                .failureConditions(request.failureConditions())
                .unknownConditions(request.unknownConditions())
                .build();
        if (current != null && current.hasSameCriteria(requested)) {
            return current;
        }
        teamTaskTypeRepository.findByTeamIdAndIsActiveTrueOrderById(team.getId()).forEach(TeamTaskType::deactivate);
        return teamTaskTypeRepository.save(requested);
    }

    private TeamMember findMembership(Long teamId, Long userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private LocalDate parseDate(String field, String value) {
        if (value.length() != 10 || value.startsWith("0000-")) {
            throw invalid(field, "YYYY-MM-DD 형식의 날짜여야 합니다.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid(field, "YYYY-MM-DD 형식의 날짜여야 합니다.");
        }
    }

    private void validatePeriod(LocalDate targetFrom, LocalDate targetTo, LocalDate onboardingStartDate) {
        long days = ChronoUnit.DAYS.between(targetFrom, targetTo) + 1;
        if (days < 1 || days > MAX_TARGET_DAYS) {
            throw invalid("targetTo", "대상 기간은 1일 이상 366일 이하여야 합니다.");
        }
        if (onboardingStartDate != null
                && (onboardingStartDate.isBefore(targetFrom) || onboardingStartDate.isAfter(targetTo))) {
            throw invalid("onboardingStartDate", "대상 기간 안의 날짜여야 합니다.");
        }
    }

    private CustomException invalid(String field, String reason) {
        return CustomException.withErrors(ErrorCode.VALIDATION_FAILED, List.of(new FieldErrorResponse(field, reason)));
    }
}
