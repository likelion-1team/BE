package com.babylion.team1.domain.team.dto.response;

import java.util.List;

public record MyTeamsResponse(List<MyTeamResponse> items, int page, int size, long total) {
}
