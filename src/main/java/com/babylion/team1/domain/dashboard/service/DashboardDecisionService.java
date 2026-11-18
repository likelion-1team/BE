package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.audit.entity.AuditEventType;
import com.babylion.team1.domain.audit.entity.AuditLog;
import com.babylion.team1.domain.audit.repository.AuditLogRepository;
import com.babylion.team1.domain.dashboard.dto.request.DecisionRecordRequest;
import com.babylion.team1.domain.dashboard.dto.response.DecisionRecordResponse;
import com.babylion.team1.domain.policy.entity.DecisionResult;
import com.babylion.team1.domain.policy.entity.PolicyRecommendation;
import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import com.babylion.team1.domain.policy.repository.PolicyRecommendationRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 대시보드 결과 기록 저장.
 * 책임자의 최종 결정을 Policy_Recommendation에 갱신하고 Audit_Log에 같은 트랜잭션으로 기록한다.
 * 실제 Policy_Version 적용(status, applied_at)은 별도 정책 배포/적용 API의 역할이다.
 */
@Service
@RequiredArgsConstructor
public class DashboardDecisionService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final UserRepository userRepository;
	private final PolicyRecommendationRepository policyRecommendationRepository;
	private final AuditLogRepository auditLogRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public DecisionRecordResponse recordDecision(Long userId, DecisionRecordRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		if (user.getRoleType() != RoleType.APPROVER) {
			throw new CustomException(ErrorCode.DECISION_PERMISSION_DENIED);
		}

		PolicyRecommendation recommendation = policyRecommendationRepository
				.findWithPolicyById(request.recommendationId())
				.orElseThrow(() -> new CustomException(ErrorCode.RECOMMENDATION_NOT_FOUND));
		Long policyId = recommendation.getPolicy().getId();
		Long companyId = recommendation.getPolicy().getCompany().getId();
		if (!companyId.equals(user.getCompany().getId())) {
			throw new CustomException(ErrorCode.RECOMMENDATION_ACCESS_DENIED);
		}

		DecisionResult decision = request.decision();
		String systemProposal = recommendation.getNextAction();
		boolean followsProposal = systemProposal != null && decision.name().equals(systemProposal.trim());
		String reason = normalize(request.reason());
		if (!followsProposal && reason == null) {
			throw new CustomException(ErrorCode.DECISION_REASON_REQUIRED);
		}

		OffsetDateTime decidedAt = OffsetDateTime.now(SEOUL).truncatedTo(ChronoUnit.SECONDS);
		int updated = policyRecommendationRepository.recordDecision(
				recommendation.getId(),
				followsProposal ? RecommendationStatus.APPROVED : RecommendationStatus.REJECTED,
				decision,
				followsProposal ? null : reason,
				user,
				decidedAt.toLocalDateTime());
		if (updated == 0) {
			throw new CustomException(ErrorCode.RECOMMENDATION_ALREADY_DECIDED);
		}

		AuditDetail detail = new AuditDetail(
				recommendation.getId(), policyId, systemProposal, decision.name(), reason);
		AuditLog auditLog = auditLogRepository.save(AuditLog.builder()
				// recordDecision이 영속성 컨텍스트를 비우므로 참조를 다시 얻는다.
				.user(userRepository.getReferenceById(userId))
				.eventType(AuditEventType.DECISION_RECORDED)
				.detail(objectMapper.writeValueAsString(detail))
				.build());

		return new DecisionRecordResponse(auditLog.getId(), recommendation.getId(), decision, decidedAt);
	}

	private String normalize(String reason) {
		if (reason == null || reason.isBlank()) {
			return null;
		}
		return reason.trim();
	}

	/** Audit_Log.detail JSON 스키마 */
	record AuditDetail(Long recommendationId, Long policyId, String systemProposal, String decision, String reason) {
	}
}
