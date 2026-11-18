package com.babylion.team1.domain.policy.service;

import com.babylion.team1.domain.audit.entity.AuditEventType;
import com.babylion.team1.domain.audit.entity.AuditLog;
import com.babylion.team1.domain.audit.repository.AuditLogRepository;
import com.babylion.team1.domain.policy.dto.request.PolicyVersionDraftRequest;
import com.babylion.team1.domain.policy.dto.response.PolicyVersionDraftResponse;
import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.policy.repository.PolicyRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PolicyVersionCommandService {
	private final UserRepository users;
	private final PolicyRepository policies;
	private final PolicyVersionRepository versions;
	private final AuditLogRepository audits;
	private final ObjectMapper objectMapper;

	@Transactional
	public PolicyVersionDraftResponse createDraft(Long userId, Long policyId, PolicyVersionDraftRequest request) {
		if (userId == null) {
			throw AuthException.unauthorized();
		}
		var user = users.findById(userId).orElseThrow(AuthException::unauthorized);
		if (user.getCompany() == null) {
			throw AuthException.unauthorized();
		}
		if (user.getRoleType() != RoleType.ADMIN && user.getRoleType() != RoleType.APPROVER) {
			throw new CustomException(ErrorCode.POLICY_DRAFT_ACCESS_DENIED);
		}
		Long companyId = user.getCompany().getId();
		var policy = policies.findByIdAndCompany_Id(policyId, companyId)
				.orElseThrow(() -> new CustomException(ErrorCode.POLICY_NOT_FOUND));
		if (request.baseVersionId() != null
				&& !versions.existsByIdAndPolicy_IdAndPolicy_Company_Id(request.baseVersionId(), policyId, companyId)) {
			throw new CustomException(ErrorCode.POLICY_VERSION_NOT_FOUND);
		}

		var version = versions.saveAndFlush(PolicyVersion.builder()
				.policy(policy).versionName(request.versionName()).status(PolicyVersionStatus.DRAFT).createdBy(user)
				.allowedTools(json(request.allowedTools())).confidenceThreshold(request.confidenceThreshold())
				.successDefinition(request.successDefinition()).qualityTimeCriteria(json(request.qualityTimeCriteria()))
				.exceptionConditions(json(request.exceptionConditions())).defaultRoute(request.defaultRoute()).build());
		audits.save(AuditLog.builder().user(user).eventType(AuditEventType.POLICY_CREATED)
				.detail(objectMapper.writeValueAsString(new DraftAuditDetail(policyId, version.getId(), request.baseVersionId())))
				.build());
		return new PolicyVersionDraftResponse(policyId, version.getId(), version.getVersionName(), version.getStatus(),
				user.getId(), version.getCreatedAt());
	}

	private String json(JsonNode node) {
		return node == null || node.isNull() ? null : objectMapper.writeValueAsString(node);
	}

	record DraftAuditDetail(Long policyId, Long versionId, Long baseVersionId) {
	}
}
