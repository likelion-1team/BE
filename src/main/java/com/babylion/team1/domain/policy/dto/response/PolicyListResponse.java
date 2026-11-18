package com.babylion.team1.domain.policy.dto.response;

import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import java.time.LocalDateTime;
import java.util.List;

public record PolicyListResponse(List<Item> items, int page, int size, long totalElements) {
	public record Item(Long policyId, String taskType, Long versionId, String versionName,
			PolicyVersionStatus status, LocalDateTime appliedAt, long projectCount) {
	}
}
