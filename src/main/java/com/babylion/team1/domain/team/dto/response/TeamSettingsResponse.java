package com.babylion.team1.domain.team.dto.response;

import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import java.time.LocalDate;
import java.util.List;

public record TeamSettingsResponse(
        List<TaskType> taskTypes,
        LocalDate targetFrom,
        LocalDate targetTo,
        LocalDate onboardingStartDate,
        Integer targetCount,
        Integer minDailyConversations,
        Integer hourlyLaborCost
) {

    public static TeamSettingsResponse from(TeamSettings settings, List<TeamTaskType> taskTypes) {
        return new TeamSettingsResponse(
                taskTypes.stream().map(TaskType::from).toList(),
                settings.getTargetFrom(),
                settings.getTargetTo(),
                settings.getOnboardingStartDate(),
                settings.getTargetCount(),
                settings.getMinDailyConversations(),
                settings.getHourlyLaborCost());
    }

    public record TaskType(Long id, String name, String successDefinition) {

        private static TaskType from(TeamTaskType taskType) {
            return new TaskType(taskType.getId(), taskType.getName(), taskType.getSuccessDefinition());
        }
    }
}
