package com.babylion.team1.domain.team.dto.response;

import com.babylion.team1.domain.team.entity.TeamRole;

public record MyTeamResponse(Long teamId, String name, TeamRole myRole, long memberCount) {
}
