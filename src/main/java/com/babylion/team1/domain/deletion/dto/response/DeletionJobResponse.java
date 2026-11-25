package com.babylion.team1.domain.deletion.dto.response;

import com.babylion.team1.domain.deletion.entity.DeletionJob;
import com.babylion.team1.domain.deletion.entity.DeletionJobStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record DeletionJobResponse(Long deletionJobId, DeletionJobStatus status, OffsetDateTime requestedAt,
                                  OffsetDateTime completedAt, String failureCode) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static DeletionJobResponse from(DeletionJob job) {
        return new DeletionJobResponse(job.getId(), job.getStatus(), inKst(job.getRequestedAt()),
                inKst(job.getCompletedAt()), job.getFailureCode());
    }

    private static OffsetDateTime inKst(Instant instant) {
        return instant == null ? null : instant.atZone(KST).toOffsetDateTime();
    }
}
