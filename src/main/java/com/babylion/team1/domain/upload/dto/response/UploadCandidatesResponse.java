package com.babylion.team1.domain.upload.dto.response;

import com.babylion.team1.domain.upload.entity.UploadCandidate.DuplicateType;
import java.time.OffsetDateTime;
import java.util.List;

public record UploadCandidatesResponse(List<Item> items, int page, int size, long total) {
    public record Item(String selectionId, String title, OffsetDateTime startedAt, String externalId,
                       DuplicateType duplicateType, boolean selectable, String validationCode) {
    }
}
