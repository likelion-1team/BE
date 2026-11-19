package com.babylion.team1.domain.policy.service;

import com.babylion.team1.domain.policy.dto.request.PolicyRecommendationListRequest;
import com.babylion.team1.domain.policy.dto.response.PolicyRecommendationDetailResponse;
import com.babylion.team1.domain.policy.dto.response.PolicyRecommendationListResponse;
import com.babylion.team1.domain.policy.repository.PolicyRecommendationRepository;
import com.babylion.team1.domain.user.entity.RoleType;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.auth.AuthException;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyRecommendationQueryService {
	private final UserRepository users;
	private final PolicyRecommendationRepository recommendations;
	private final ObjectMapper objectMapper;

	public PolicyRecommendationListResponse getRecommendations(Long userId, PolicyRecommendationListRequest request) {
		Long companyId = requireCompanyId(userId);
		var result = recommendations.findRecommendationList(companyId, request.policyId(), request.status(),
				PageRequest.of(request.page(), request.size()));
		var items = result.getContent().stream()
				.map(row -> new PolicyRecommendationListResponse.Item(row.getRecommendationId(), row.getPolicyId(),
						row.getTaskType(), row.getStatus(), row.getObservedProblem(), row.getSampleCount(), row.getCreatedAt()))
				.toList();
		return new PolicyRecommendationListResponse(items, result.getNumber(), result.getSize(), result.getTotalElements());
	}

	public PolicyRecommendationDetailResponse getRecommendationDetail(Long userId, Long recommendationId) {
		Long companyId = requireCompanyId(userId);
		var recommendation = recommendations.findRecommendationDetail(recommendationId, companyId)
				.orElseThrow(() -> new CustomException(ErrorCode.RECOMMENDATION_NOT_FOUND));
		return new PolicyRecommendationDetailResponse(recommendation.getId(), recommendation.getPolicy().getId(),
				recommendation.getStatus(), recommendation.getObservedProblem(), recommendation.getCauseCandidate(),
				recommendation.getNextAction(), recommendation.getDetail() == null ? null
						: objectMapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(recommendation.getDetail()),
				recommendation.getSampleCount(), recommendation.getRejectionReason(),
				recommendation.getDecidedBy() == null ? null : recommendation.getDecidedBy().getId(),
				recommendation.getDecidedAt(), recommendation.getCreatedAt(),
				recommendation.getGeneratedVersion() == null ? null : recommendation.getGeneratedVersion().getId());
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
