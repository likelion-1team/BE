package com.babylion.team1.domain.policy.service;

import com.babylion.team1.domain.audit.entity.AuditEventType;
import com.babylion.team1.domain.audit.entity.AuditLog;
import com.babylion.team1.domain.audit.repository.AuditLogRepository;
import com.babylion.team1.domain.policy.dto.response.PolicyApplyPreviewResponse;
import com.babylion.team1.domain.policy.dto.response.PolicyApplyResponse;
import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import com.babylion.team1.domain.policy.entity.ProjectPolicyMapping;
import com.babylion.team1.domain.policy.repository.PolicyRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository;
import com.babylion.team1.domain.policy.repository.ProjectPolicyMappingRepository;
import com.babylion.team1.domain.project.repository.ProjectRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PolicyApplyService {
	private final UserRepository users;
	private final PolicyRepository policies;
	private final PolicyVersionRepository versions;
	private final ProjectRepository projects;
	private final ProjectPolicyMappingRepository mappings;
	private final AuditLogRepository audits;
	private final ObjectMapper mapper;

	@Transactional(readOnly = true)
	public PolicyApplyPreviewResponse preview(Long userId, Long policyId, Long versionId, List<Long> input) {
		var user = actor(userId, false);
		Long companyId = user.getCompany().getId();
		if (!policies.existsByIdAndCompany_Id(policyId, companyId)) throw new CustomException(ErrorCode.POLICY_NOT_FOUND);
		var version = version(policyId, versionId, companyId);
		var ids = ids(input);
		var projectMap = projectMap(ids, companyId);
		var current = current(policyId, ids);
		var result = ids.stream().map(id -> new PolicyApplyPreviewResponse.Project(id, projectMap.get(id).getProjectName(),
				current.containsKey(id) ? current.get(id).getPolicyVersion().getId() : null)).toList();
		return new PolicyApplyPreviewResponse(policyId, versionId, version.getVersionName(), result,
				json(version.getExceptionConditions()), json(version.getQualityTimeCriteria()));
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public PolicyApplyResponse apply(Long userId, Long policyId, Long versionId, List<Long> input) {
		var user = actor(userId, true);
		Long companyId = user.getCompany().getId();
		policies.lockForApply(policyId, companyId).orElseThrow(() -> new CustomException(ErrorCode.POLICY_NOT_FOUND));
		var version = version(policyId, versionId, companyId);
		if (version.getStatus() != PolicyVersionStatus.DRAFT || version.getAppliedAt() != null || version.getApprovedBy() != null)
			throw new CustomException(ErrorCode.POLICY_APPLY_CONFLICT);
		var ids = ids(input);
		var projectMap = projectMap(ids, companyId);
		var current = current(policyId, ids);
		var previous = new ArrayList<Previous>();
		for (Long id : ids) {
			var old = current.get(id);
			previous.add(new Previous(id, old == null ? null : old.getPolicyVersion().getId()));
			if (old == null) mappings.save(ProjectPolicyMapping.builder().project(projectMap.get(id)).policyVersion(version).build());
			else old.changeVersion(version);
		}
		var now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.SECONDS);
		version.apply(user, now);
		audits.save(AuditLog.builder().user(user).eventType(AuditEventType.POLICY_APPROVED)
				.detail(mapper.writeValueAsString(new ApplyAudit(policyId, versionId, previous))).build());
		return new PolicyApplyResponse(policyId, versionId, version.getStatus(), user.getId(), now, ids);
	}

	private User actor(Long userId, boolean write) {
		if (userId == null) throw AuthException.unauthorized();
		var user = users.findById(userId).orElseThrow(AuthException::unauthorized);
		if (user.getCompany() == null) throw AuthException.unauthorized();
		if (write && user.getRoleType() != RoleType.APPROVER) throw new CustomException(ErrorCode.POLICY_APPLY_ACCESS_DENIED);
		if (!write && user.getRoleType() != RoleType.ADMIN && user.getRoleType() != RoleType.APPROVER)
			throw new CustomException(ErrorCode.POLICY_ACCESS_DENIED);
		return user;
	}
	private PolicyVersion version(Long policyId, Long versionId, Long companyId) {
		return versions.findVersionDetail(policyId, versionId, companyId)
				.orElseThrow(() -> new CustomException(ErrorCode.POLICY_VERSION_NOT_FOUND));
	}
	private List<Long> ids(List<Long> input) {
		if (input == null || input.isEmpty() || input.stream().anyMatch(id -> id == null || id <= 0))
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		return List.copyOf(new LinkedHashSet<>(input));
	}
	private Map<Long, com.babylion.team1.domain.project.entity.Project> projectMap(List<Long> ids, Long companyId) {
		var result = projects.findByIdInAndCompany_Id(ids, companyId).stream()
				.collect(Collectors.toMap(com.babylion.team1.domain.project.entity.Project::getId, Function.identity()));
		if (result.size() != ids.size()) throw new CustomException(ErrorCode.POLICY_APPLY_PROJECT_NOT_FOUND);
		return result;
	}
	private Map<Long, ProjectPolicyMapping> current(Long policyId, List<Long> ids) {
		var rows = mappings.findCurrentForProjects(policyId, ids);
		var result = new java.util.HashMap<Long, ProjectPolicyMapping>();
		for (var row : rows) if (result.putIfAbsent(row.getProject().getId(), row) != null)
			throw new CustomException(ErrorCode.POLICY_APPLY_CONFLICT);
		return result;
	}
	private JsonNode json(String value) { return value == null ? null : mapper.readTree(value); }
	record Previous(Long projectId, Long versionId) {}
	record ApplyAudit(Long policyId, Long versionId, List<Previous> previous) {}
}
