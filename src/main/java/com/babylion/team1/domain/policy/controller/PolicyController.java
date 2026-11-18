package com.babylion.team1.domain.policy.controller;

import com.babylion.team1.domain.policy.dto.request.PolicyListRequest;
import com.babylion.team1.domain.policy.dto.request.PolicyVersionDraftRequest;
import com.babylion.team1.domain.policy.dto.request.PolicyVersionHistoryRequest;
import com.babylion.team1.domain.policy.dto.response.PolicyListResponse;
import com.babylion.team1.domain.policy.dto.response.PolicyVersionDetailResponse;
import com.babylion.team1.domain.policy.dto.response.PolicyVersionDiffResponse;
import com.babylion.team1.domain.policy.dto.response.PolicyVersionDraftResponse;
import com.babylion.team1.domain.policy.dto.response.PolicyVersionHistoryResponse;
import com.babylion.team1.domain.policy.service.PolicyQueryService;
import com.babylion.team1.domain.policy.service.PolicyVersionCommandService;
import com.babylion.team1.global.auth.AuthenticatedUserId;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Policy", description = "정책 관리자 API")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {
	private final PolicyQueryService policyQueryService;
	private final PolicyVersionCommandService policyVersionCommandService;

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

	@Operation(summary = "정책 버전 이력 목록 조회",
			description = "ADMIN·APPROVER가 같은 회사 정책의 모든 상태(DRAFT·ACTIVE·ROLLED_BACK)를 조회합니다. "
					+ "생성일과 ID 내림차순이며 projects는 현재 매핑된 같은 회사 프로젝트의 중복 없는 목록입니다. "
					+ "과거 적용 시점의 프로젝트 목록은 제공하지 않습니다.")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping("/{policyId}/versions")
	public ResponseEntity<ApiResponse<PolicyVersionHistoryResponse>> getVersionHistory(
			@Parameter(hidden = true) @AuthenticatedUserId Long userId,
			@Parameter(description = "정책 ID", example = "1") @PathVariable @Positive Long policyId,
			@Valid @ModelAttribute @ParameterObject PolicyVersionHistoryRequest request) {
		return ResponseEntity.ok(ApiResponse.success("S200", "정책 버전 이력 목록 조회에 성공했습니다",
				policyQueryService.getVersionHistory(userId, policyId, request)));
	}

	@Operation(summary = "특정 정책 버전 상세 조회",
			description = "ADMIN·APPROVER가 같은 회사·같은 정책에 속한 버전의 설정을 조회합니다. "
					+ "JSON 설정은 저장된 객체·배열 구조로, 작성자·승인자는 ID로 반환합니다. "
					+ "미존재·타사 정책은 POLICY_NOT_FOUND, 정책에 속하지 않는 버전은 POLICY_VERSION_NOT_FOUND(404)입니다.")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping("/{policyId}/versions/{versionId}")
	public ResponseEntity<ApiResponse<PolicyVersionDetailResponse>> getVersionDetail(
			@Parameter(hidden = true) @AuthenticatedUserId Long userId,
			@Parameter(description = "정책 ID", example = "1") @PathVariable @Positive Long policyId,
			@Parameter(description = "정책에 속한 버전 ID", example = "10") @PathVariable @Positive Long versionId) {
		return ResponseEntity.ok(ApiResponse.success("S200", "특정 정책 버전 상세 조회에 성공했습니다",
				policyQueryService.getVersionDetail(userId, policyId, versionId)));
	}

	@Operation(summary = "새 정책 초안 생성",
			description = "ADMIN·APPROVER가 같은 회사 정책에 새 DRAFT를 생성합니다. "
					+ "기준 버전은 소속 확인용이며 요청 설정 전체를 저장하고 누락값을 자동 복사하지 않습니다. "
					+ "기존 버전·프로젝트 매핑을 유지하고 생성 감사 로그를 같은 트랜잭션으로 저장합니다.")
	@SecurityRequirement(name = "bearerAuth")
	@PostMapping("/{policyId}/versions")
	public ResponseEntity<ApiResponse<PolicyVersionDraftResponse>> createDraft(
			@Parameter(hidden = true) @AuthenticatedUserId Long userId,
			@Parameter(description = "정책 ID", example = "1") @PathVariable @Positive Long policyId,
			@Valid @RequestBody PolicyVersionDraftRequest request) {
		return ResponseEntity.ok(ApiResponse.success("S200", "새 정책 초안 생성에 성공했습니다",
				policyVersionCommandService.createDraft(userId, policyId, request)));
	}

	@Operation(summary = "정책 버전 Diff 비교",
			description = "ADMIN·APPROVER가 같은 회사·같은 정책에 속한 두 버전의 설정 6개를 비교합니다. "
					+ "JSON 객체 키 순서는 무시하고 배열 순서는 구분하며 숫자는 값으로 비교합니다. "
					+ "중첩 변경은 최상위 설정 필드 전체로 반환하고 같은 버전·변경 없음은 changes=[]입니다.")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping("/{policyId}/versions/diff")
	public ResponseEntity<ApiResponse<PolicyVersionDiffResponse>> getVersionDiff(
			@Parameter(hidden = true) @AuthenticatedUserId Long userId,
			@Parameter(description = "정책 ID", example = "1") @PathVariable @Positive Long policyId,
			@Parameter(description = "기준 버전 ID", example = "10") @RequestParam @Positive Long baseId,
			@Parameter(description = "비교 버전 ID", example = "11") @RequestParam @Positive Long targetId) {
		return ResponseEntity.ok(ApiResponse.success("S200", "정책 버전 Diff 비교에 성공했습니다",
				policyQueryService.getVersionDiff(userId, policyId, baseId, targetId)));
	}
}
