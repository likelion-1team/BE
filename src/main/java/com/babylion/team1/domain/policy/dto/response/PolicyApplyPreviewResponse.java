package com.babylion.team1.domain.policy.dto.response;

import java.util.List;
import tools.jackson.databind.JsonNode;

public record PolicyApplyPreviewResponse(Long policyId, Long versionId, String versionName,
		List<Project> projects, JsonNode exceptionConditions, JsonNode qualityTimeCriteria) {
	public record Project(Long projectId, String projectName, Long currentVersionId) {}
}
