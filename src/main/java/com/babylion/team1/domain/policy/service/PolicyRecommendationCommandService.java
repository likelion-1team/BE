package com.babylion.team1.domain.policy.service;

import com.babylion.team1.domain.audit.entity.AuditEventType;
import com.babylion.team1.domain.audit.entity.AuditLog;
import com.babylion.team1.domain.audit.repository.AuditLogRepository;
import com.babylion.team1.domain.policy.dto.request.PolicyRecommendationApproveRequest;
import com.babylion.team1.domain.policy.dto.request.PolicyRecommendationRejectRequest;
import com.babylion.team1.domain.policy.dto.response.PolicyRecommendationApproveResponse;
import com.babylion.team1.domain.policy.dto.response.PolicyRecommendationRejectResponse;
import com.babylion.team1.domain.policy.entity.PolicyRecommendation;
import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import com.babylion.team1.domain.policy.repository.PolicyRecommendationRepository;
import com.babylion.team1.domain.policy.repository.PolicyVersionRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional
public class PolicyRecommendationCommandService {
	private final UserRepository users;
	private final PolicyRecommendationRepository recommendations;
	private final PolicyVersionRepository versions;
	private final PolicyVersionCommandService drafts;
	private final AuditLogRepository audits;
	private final ObjectMapper objectMapper;

	public PolicyRecommendationApproveResponse approve(Long userId, Long recommendationId, PolicyRecommendationApproveRequest request) {
		var actor = requireApprover(userId);
		var recommendation = requirePending(recommendationId, actor);
		Long policyId = recommendation.getPolicy().getId();
		var decidedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.SECONDS);
		claim(recommendationId, actor, RecommendationStatus.APPROVED, null, decidedAt);

		// 별도 Bean의 초안 생성 트랜잭션도 현재 결정 트랜잭션에 참여한다.
		var draft = drafts.createDraft(userId, policyId, request.toDraftRequest());
		if (recommendations.linkGeneratedVersion(recommendationId, userId, versions.getReferenceById(draft.versionId())) != 1) {
			throw new IllegalStateException("승인된 추천과 생성 초안을 연결하지 못했습니다.");
		}
		audit(actor.getId(), AuditEventType.RECOMMENDATION_APPROVED,
				new ApprovalAuditDetail(recommendationId, policyId, draft.versionId()));
		return new PolicyRecommendationApproveResponse(recommendationId, RecommendationStatus.APPROVED,
				userId, decidedAt, policyId, draft.versionId(), draft.status());
	}

	public PolicyRecommendationRejectResponse reject(Long userId, Long recommendationId, PolicyRecommendationRejectRequest request) {
		var actor = requireApprover(userId);
		var recommendation = requirePending(recommendationId, actor);
		Long policyId = recommendation.getPolicy().getId();
		var decidedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.SECONDS);
		claim(recommendationId, actor, RecommendationStatus.REJECTED, request.rejectionReason(), decidedAt);
		audit(actor.getId(), AuditEventType.RECOMMENDATION_REJECTED,
				new RejectionAuditDetail(recommendationId, policyId, request.rejectionReason()));
		return new PolicyRecommendationRejectResponse(recommendationId, RecommendationStatus.REJECTED,
				request.rejectionReason(), userId, decidedAt);
	}

	private User requireApprover(Long userId) {
		if (userId == null) {
			throw AuthException.unauthorized();
		}
		var user = users.findById(userId).orElseThrow(AuthException::unauthorized);
		if (user.getCompany() == null) {
			throw AuthException.unauthorized();
		}
		if (user.getRoleType() != RoleType.APPROVER) {
			throw new CustomException(ErrorCode.DECISION_PERMISSION_DENIED);
		}
		return user;
	}

	private PolicyRecommendation requirePending(Long id, User actor) {
		var recommendation = recommendations.findRecommendationDetail(id, actor.getCompany().getId())
				.orElseThrow(() -> new CustomException(ErrorCode.RECOMMENDATION_NOT_FOUND));
		if (recommendation.getStatus() != RecommendationStatus.PENDING || recommendation.getDecidedAt() != null
				|| recommendation.getGeneratedVersion() != null) {
			throw new CustomException(ErrorCode.RECOMMENDATION_ALREADY_DECIDED);
		}
		return recommendation;
	}

	private void claim(Long id, User actor, RecommendationStatus status, String reason, LocalDateTime decidedAt) {
		int changed = recommendations.recordPolicyDecision(id, actor.getCompany().getId(), status, reason, actor, decidedAt);
		if (changed == 0) {
			throw new CustomException(ErrorCode.RECOMMENDATION_ALREADY_DECIDED);
		}
	}

	private void audit(Long userId, AuditEventType event, Object detail) {
		// 조건부 갱신이 영속성 컨텍스트를 비우므로 작성자 참조를 다시 얻는다.
		audits.save(AuditLog.builder().user(users.getReferenceById(userId)).eventType(event)
				.detail(objectMapper.writeValueAsString(detail)).build());
	}

	record ApprovalAuditDetail(Long recommendationId, Long policyId, Long versionId) {
	}

	record RejectionAuditDetail(Long recommendationId, Long policyId, String rejectionReason) {
	}
}
