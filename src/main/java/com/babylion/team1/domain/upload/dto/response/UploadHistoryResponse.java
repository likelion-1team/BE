package com.babylion.team1.domain.upload.dto.response;

import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;

public record UploadHistoryResponse(List<Item> content, int page, int size, long totalElements) {

    public record Item(
            Long uploadId,
            String uploadedAt,
            long analyzedCount,
            @JsonInclude(JsonInclude.Include.ALWAYS) Period period,
            UploadStatus status
    ) {
    }

    public record Period(LocalDate from, LocalDate to) {
    }
}
