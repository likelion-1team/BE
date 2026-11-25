package com.babylion.team1.domain.deletion.dto.response;

import com.babylion.team1.domain.deletion.entity.DeletionJob;
import com.babylion.team1.domain.deletion.entity.DeletionJobStatus;

public record DeletionRequestResponse(Long deletionJobId, DeletionJobStatus status, String statusUrl) {

    public static DeletionRequestResponse from(DeletionJob job) {
        return new DeletionRequestResponse(job.getId(), job.getStatus(), "/api/v1/deletion-jobs/" + job.getId());
    }
}
