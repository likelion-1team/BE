package com.babylion.team1.domain.policy.controller;

import com.babylion.team1.domain.policy.dto.request.PolicyListRequest;
import com.babylion.team1.domain.policy.dto.response.PolicyListResponse;
import com.babylion.team1.domain.policy.service.PolicyQueryService;
import com.babylion.team1.global.auth.AuthenticatedUserId;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Policy", description = "정책 관리자 API")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {
	private final PolicyQueryService policyQueryService;

	@Operation(summary = "업무 유형별 정책 목록 조회",
			description = "ADMIN·APPROVER가 같은 회사의 정책을 버전 단위로 조회합니다. "
					+ "상태 생략 시 ACTIVE·ROLLED_BACK을 반환하며 생성일과 ID 내림차순으로 정렬합니다. "
					+ "projectCount는 현재 매핑된 같은 회사의 서로 다른 프로젝트 수입니다.")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping
	public ResponseEntity<ApiResponse<PolicyListResponse>> getPolicies(
			@Parameter(hidden = true) @AuthenticatedUserId Long userId,
			@Valid @ModelAttribute @ParameterObject PolicyListRequest request) {
		return ResponseEntity.ok(ApiResponse.success("S200", "업무 유형별 정책 목록 조회에 성공했습니다",
				policyQueryService.getPolicies(userId, request)));
	}
}
