package com.babylion.team1.domain.upload.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record UploadResponse(Long uploadId, List<Candidate> conversations) {

    public record Candidate(String externalId, String title, OffsetDateTime startedAt, boolean isDuplicate) {
    }
}
