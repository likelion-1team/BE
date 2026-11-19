package com.babylion.team1.domain.policy.controller;

import com.babylion.team1.domain.policy.dto.request.PolicyRecommendationListRequest;
import com.babylion.team1.domain.policy.dto.response.PolicyRecommendationDetailResponse;
import com.babylion.team1.domain.policy.dto.response.PolicyRecommendationListResponse;
import com.babylion.team1.domain.policy.service.PolicyRecommendationQueryService;
import com.babylion.team1.global.auth.AuthenticatedUserId;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Policy Recommendation", description = "정책 추천 조회 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/policy-recommendations")
@RequiredArgsConstructor
public class PolicyRecommendationController {
	private final PolicyRecommendationQueryService service;

	@Operation(summary = "정책 변경 추천 리스트 조회", description = "ADMIN·APPROVER가 같은 회사의 추천을 조회합니다. "
			+ "status 생략 시 전체 상태이며 생성일·ID 내림차순입니다. 미존재·타사 policyId 필터는 빈 목록을 반환합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<PolicyRecommendationListResponse>> getRecommendations(
			@Parameter(hidden = true) @AuthenticatedUserId Long userId,
			@Valid @ModelAttribute @ParameterObject PolicyRecommendationListRequest request) {
		return ResponseEntity.ok(ApiResponse.success("S200", "정책 변경 추천 리스트 조회에 성공했습니다",
				service.getRecommendations(userId, request)));
	}

	@Operation(summary = "정책 추천 아코디언 상세", description = "ADMIN·APPROVER가 같은 회사 추천의 상세·결정 정보를 조회합니다. "
			+ "detail은 저장된 JSON, decidedBy는 ID 또는 null입니다. 미존재·타사 추천은 RECOMMENDATION_NOT_FOUND(404)입니다.")
	@GetMapping("/{recommendationId}")
	public ResponseEntity<ApiResponse<PolicyRecommendationDetailResponse>> getRecommendationDetail(
			@Parameter(hidden = true) @AuthenticatedUserId Long userId,
			@Parameter(description = "추천 ID", example = "21") @PathVariable @Positive Long recommendationId) {
		return ResponseEntity.ok(ApiResponse.success("S200", "정책 추천 아코디언 상세에 성공했습니다",
				service.getRecommendationDetail(userId, recommendationId)));
	}
}
