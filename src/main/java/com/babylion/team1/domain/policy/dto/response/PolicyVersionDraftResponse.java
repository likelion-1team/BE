package com.babylion.team1.domain.policy.dto.response;

import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import java.time.LocalDateTime;

public record PolicyVersionDraftResponse(Long policyId, Long versionId, String versionName,
		PolicyVersionStatus status, Long createdBy, LocalDateTime createdAt) {
}
