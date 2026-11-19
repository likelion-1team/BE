package com.babylion.team1.domain.policy.dto.response;

import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import java.time.LocalDateTime;
import java.util.List;

public record PolicyApplyResponse(Long policyId, Long versionId, PolicyVersionStatus status,
		Long approvedBy, LocalDateTime appliedAt, List<Long> projectIds) {}
