package com.babylion.team1.domain.policy.dto.response;

import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record PolicyVersionHistoryResponse(Long policyId, List<Item> items, int page, int size, long totalElements) {
	@Schema(name = "PolicyVersionHistoryItem")
	public record Item(Long versionId, String versionName, PolicyVersionStatus status, Author createdBy,
			LocalDateTime createdAt, LocalDateTime appliedAt, List<Project> projects) {
	}

	@Schema(name = "PolicyVersionHistoryAuthor")
	public record Author(Long userId, String userName) {
	}

	@Schema(name = "PolicyVersionHistoryProject")
	public record Project(Long projectId, String projectName) {
	}
}
