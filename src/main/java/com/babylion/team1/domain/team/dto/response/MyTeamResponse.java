package com.babylion.team1.domain.team.dto.response;

import com.babylion.team1.domain.team.entity.TeamRole;

public record MyTeamResponse(
        Long teamId,
        String name,
        String description,
        TeamRole myRole,
        long memberCount,
        Capabilities capabilities
) {
    public MyTeamResponse(Long teamId, String name, String description, TeamRole myRole, long memberCount) {
        this(teamId, name, description, myRole, memberCount, Capabilities.forRole(myRole));
    }

    public record Capabilities(
            boolean canViewOwnData,
            boolean canViewTeamMetrics,
            boolean canEditSettings,
            boolean canEditGoals,
            boolean canManageMembers,
            boolean canManageSharedCases
    ) {
        /** 개인 성과와 팀 성과는 팀원도 조회할 수 있고, 나머지는 팀장만 할 수 있다 */
        private static Capabilities forRole(TeamRole role) {
            boolean leader = role == TeamRole.LEADER;
            return new Capabilities(true, true, leader, leader, leader, leader);
        }
    }
}
