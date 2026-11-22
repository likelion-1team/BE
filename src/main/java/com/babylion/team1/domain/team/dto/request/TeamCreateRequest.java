package com.babylion.team1.domain.team.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {
}
