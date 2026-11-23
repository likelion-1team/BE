package com.babylion.team1.domain.team.dto.response;

import com.babylion.team1.domain.team.entity.TeamRole;

public record TeamMemberListResponse(Long userId, String name, String email, TeamRole role, boolean submitted) {
}
