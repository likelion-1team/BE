package com.babylion.team1.domain.policy.service;

import com.babylion.team1.domain.policy.dto.request.PolicyListRequest;
import com.babylion.team1.domain.policy.dto.response.PolicyListResponse;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository.PolicyVersionListRow;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository.VersionProjectCount;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyQueryService {
	private final UserRepository users;
	private final PolicyVersionRepository versions;
	private final ProjectPolicyMappingRepository mappings;

	public PolicyListResponse getPolicies(Long userId, PolicyListRequest request) {
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

		Long companyId = user.getCompany().getId();
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
}
