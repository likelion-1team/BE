package com.babylion.team1.domain.team.dto.response;

import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.global.policy.ServerPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 저장 전이면 version은 0, taskType은 null이다. */
public record TeamSettingsResponse(
        int version,
        TaskType taskType,
        LocalDate targetFrom,
        LocalDate targetTo,
        LocalDate onboardingStartDate,
        Integer targetCount,
        OffsetDateTime updatedAt,
        Policies policies
) {

    public static TeamSettingsResponse from(TeamSettings settings) {
        TeamTaskType taskType = settings.getTaskType();
        return new TeamSettingsResponse(
                settings.getVersion(),
                taskType == null ? null : TaskType.from(taskType),
                settings.getTargetFrom(),
                settings.getTargetTo(),
                settings.getOnboardingStartDate(),
                settings.getTargetCount(),
                settings.updatedAtInKst(),
                Policies.current());
    }

    /** 설정 행이 없는 팀 */
    public static TeamSettingsResponse unset() {
        return new TeamSettingsResponse(0, null, null, null, null, null, null, Policies.current());
    }

    public record TaskType(
            Long taskTypeId,
            String name,
            List<String> successConditions,
            List<String> failureConditions,
            List<String> unknownConditions
    ) {

        private static TaskType from(TeamTaskType taskType) {
            return new TaskType(taskType.getId(), taskType.getName(), taskType.getSuccessConditions(),
                    taskType.getFailureConditions(), taskType.getUnknownConditions());
        }
    }

    public record Policies(
            int policyVersion,
            int minSampleSize,
            int minTeamContributors,
            BigDecimal minOutcomeConfirmationRate,
            int uploadRetentionMinutes,
            int maxSelectionsPerRequest
    ) {

        private static Policies current() {
            return new Policies(
                    ServerPolicy.VERSION,
                    ServerPolicy.MIN_SAMPLE_SIZE,
                    ServerPolicy.MIN_TEAM_CONTRIBUTORS,
                    ServerPolicy.MIN_OUTCOME_CONFIRMATION_RATE,
                    ServerPolicy.UPLOAD_RETENTION_MINUTES,
                    ServerPolicy.MAX_SELECTIONS_PER_REQUEST);
        }
    }
}
