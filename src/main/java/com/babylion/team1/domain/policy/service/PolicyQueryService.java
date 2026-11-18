package com.babylion.team1.domain.policy.service;

import com.babylion.team1.domain.policy.dto.request.PolicyListRequest;
import com.babylion.team1.domain.policy.dto.request.PolicyVersionHistoryRequest;
import com.babylion.team1.domain.policy.dto.response.PolicyListResponse;
import com.babylion.team1.domain.policy.dto.response.PolicyVersionDetailResponse;
import com.babylion.team1.domain.policy.dto.response.PolicyVersionHistoryResponse;
import com.babylion.team1.domain.policy.dto.response.PolicyVersionHistoryResponse.Author;
import com.babylion.team1.domain.policy.dto.response.PolicyVersionHistoryResponse.Project;
import com.babylion.team1.domain.policy.repository.PolicyRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository.PolicyVersionHistoryRow;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository.PolicyVersionListRow;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository.VersionProjectCount;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository.VersionProjectRow;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {
	private final UserRepository users;
	private final PolicyVersionRepository versions;
	private final ProjectPolicyMappingRepository mappings;
	private final PolicyRepository policies;
	private final ObjectMapper objectMapper;

	public PolicyListResponse getPolicies(Long userId, PolicyListRequest request) {
		Long companyId = requireCompanyId(userId);
		var result = versions.findPolicyList(companyId, request.taskType(), request.statuses(),
				PageRequest.of(request.page(), request.size()));
		Map<Long, Long> projectCounts = result.isEmpty() ? Map.of()
				: mappings.countProjectsByVersionIds(result.getContent().stream()
						.map(PolicyVersionListRow::getVersionId).toList(), companyId).stream()
						.collect(Collectors.toMap(VersionProjectCount::getVersionId, VersionProjectCount::getProjectCount));
		var items = result.getContent().stream()
				.map(row -> new PolicyListResponse.Item(row.getPolicyId(), row.getTaskType(), row.getVersionId(),
						row.getVersionName(), row.getStatus(), row.getAppliedAt(),
						projectCounts.getOrDefault(row.getVersionId(), 0L)))
				.toList();
		return new PolicyListResponse(items, result.getNumber(), result.getSize(), result.getTotalElements());
	}

	public PolicyVersionHistoryResponse getVersionHistory(Long userId, Long policyId, PolicyVersionHistoryRequest request) {
		Long companyId = requireCompanyId(userId);
		requirePolicy(policyId, companyId);
		var result = versions.findVersionHistory(policyId, companyId, PageRequest.of(request.page(), request.size()));
		Map<Long, List<Project>> projects = result.isEmpty() ? Map.of()
				: mappings.findProjectsByVersionIds(result.getContent().stream()
						.map(PolicyVersionHistoryRow::getVersionId).toList(), companyId).stream()
						.collect(Collectors.groupingBy(VersionProjectRow::getVersionId,
								Collectors.mapping(row -> new Project(row.getProjectId(), row.getProjectName()), Collectors.toList())));
		var items = result.getContent().stream()
				.map(row -> new PolicyVersionHistoryResponse.Item(row.getVersionId(), row.getVersionName(), row.getStatus(),
						new Author(row.getCreatedById(), row.getCreatedByName()), row.getCreatedAt(), row.getAppliedAt(),
						projects.getOrDefault(row.getVersionId(), List.of())))
				.toList();
		return new PolicyVersionHistoryResponse(policyId, items, result.getNumber(), result.getSize(), result.getTotalElements());
	}

	public PolicyVersionDetailResponse getVersionDetail(Long userId, Long policyId, Long versionId) {
		Long companyId = requireCompanyId(userId);
		requirePolicy(policyId, companyId);
		var version = versions.findVersionDetail(policyId, versionId, companyId)
				.orElseThrow(() -> new CustomException(ErrorCode.POLICY_VERSION_NOT_FOUND));
		return new PolicyVersionDetailResponse(version.getPolicy().getId(), version.getPolicy().getTaskType(),
				version.getId(), version.getVersionName(), version.getStatus(), version.getConfidenceThreshold(),
				parseJson(version.getAllowedTools()), version.getSuccessDefinition(), parseJson(version.getQualityTimeCriteria()),
				parseJson(version.getExceptionConditions()), version.getDefaultRoute(), version.getCreatedBy().getId(),
				version.getCreatedAt(), version.getApprovedBy() == null ? null : version.getApprovedBy().getId(),
				version.getAppliedAt(), version.getRolledBackAt(), version.getRollbackReason());
	}

	private JsonNode parseJson(String value) {
		return value == null ? null : objectMapper.readTree(value);
	}

	private void requirePolicy(Long policyId, Long companyId) {
		if (!policies.existsByIdAndCompany_Id(policyId, companyId)) {
			throw new CustomException(ErrorCode.POLICY_NOT_FOUND);
		}
	}

	private Long requireCompanyId(Long userId) {
		if (userId == null) {
			throw AuthException.unauthorized();
		}
		var user = users.findById(userId).orElseThrow(AuthException::unauthorized);
		if (user.getCompany() == null) {
			throw AuthException.unauthorized();
		}
		if (user.getRoleType() != RoleType.ADMIN && user.getRoleType() != RoleType.APPROVER) {
			throw new CustomException(ErrorCode.POLICY_ACCESS_DENIED);
		}

		return user.getCompany().getId();
	}
}
