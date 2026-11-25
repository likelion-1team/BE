package com.babylion.team1.domain.team.dto.response;

import com.babylion.team1.domain.team.entity.TeamRole;

public record TeamCreateResponse(Long teamId, String name, String description, TeamRole myRole) {
}
