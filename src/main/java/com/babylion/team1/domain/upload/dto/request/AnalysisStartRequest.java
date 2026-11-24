package com.babylion.team1.domain.upload.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AnalysisStartRequest(Integer settingsVersion, List<@NotNull @Valid CandidateSelection> selections) {

    public record CandidateSelection(String selectionId, Long taskTypeId) {
    }

    public record Selection(@NotBlank @Size(max = 100) String externalId, @NotNull @Positive Long taskTypeId) {
    }
}
