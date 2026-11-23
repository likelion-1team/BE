package com.babylion.team1.domain.team.dto.response;

import com.babylion.team1.domain.team.entity.TeamRole;

public record TeamMemberAddResponse(Long userId, String name, TeamRole role) {
}
