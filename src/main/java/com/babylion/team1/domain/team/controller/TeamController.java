package com.babylion.team1.domain.team.controller;

import com.babylion.team1.domain.feedback.dto.request.MyFeedbackSort;
import com.babylion.team1.domain.feedback.dto.response.GuidesResponse;
import com.babylion.team1.domain.feedback.dto.response.MyFeedbackListResponse;
import com.babylion.team1.domain.feedback.dto.response.SharedCasesResponse;
import com.babylion.team1.domain.team.dto.request.TeamCreateRequest;
import com.babylion.team1.domain.team.dto.request.TeamMemberAddRequest;
import com.babylion.team1.domain.team.dto.request.TeamSettingsUpdateRequest;
import com.babylion.team1.domain.team.dto.response.MyTeamResponse;
import com.babylion.team1.domain.team.dto.response.TeamCreateResponse;
import com.babylion.team1.domain.team.dto.response.TeamMemberAddResponse;
import com.babylion.team1.domain.team.dto.response.TeamMemberListResponse;
import com.babylion.team1.domain.team.dto.response.TeamSettingsResponse;
import com.babylion.team1.domain.team.dto.response.TeamSettingsUpdateResponse;
import com.babylion.team1.domain.team.dto.response.TeamSubmissionResponse;
import com.babylion.team1.domain.team.service.TeamService;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    @Operation(summary = "팀 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TeamCreateResponse> createTeam(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamCreateRequest request) {
        return ApiResponse.success("S201", "팀 생성 성공", teamService.createTeam(userId, request));
    }

    @Operation(summary = "내 팀 목록 조회")
    @GetMapping
    public ApiResponse<List<MyTeamResponse>> getMyTeams(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S200", "조회 성공", teamService.getMyTeams(userId));
    }

    @Operation(summary = "팀원 추가")
    @PostMapping("/{teamId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TeamMemberAddResponse> addMember(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamMemberAddRequest request) {
        return ApiResponse.success("S201", "팀원 추가 성공", teamService.addMember(teamId, userId, request));
    }

    @Operation(summary = "팀원 목록 및 제출 여부 조회")
    @GetMapping("/{teamId}/members")
    public ApiResponse<List<TeamMemberListResponse>> getTeamMembers(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S200", "조회 성공", teamService.getTeamMembers(teamId, userId));
    }

    @Operation(summary = "팀 제출 현황 조회",
            description = "팀장이 대상 기간의 제출 인원과 분석 완료 대화 수를 조회합니다.")
    @GetMapping("/{teamId}/submissions")
    public ApiResponse<TeamSubmissionResponse> getTeamSubmissions(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S200", "조회 성공", teamService.getTeamSubmissions(teamId, userId));
    }

    @Operation(summary = "내 피드백 목록 조회",
            description = "날짜 그룹은 최신순으로 한 페이지에 20개씩 반환합니다. sort는 각 그룹 안에 적용합니다.")
    @GetMapping("/{teamId}/feedbacks")
    public ApiResponse<MyFeedbackListResponse> getMyFeedbacks(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> types,
            @RequestParam(defaultValue = "LATEST") MyFeedbackSort sort,
            @RequestParam(defaultValue = "0") int page) {
        return ApiResponse.success("S200", "조회 성공",
                teamService.getMyFeedbacks(teamId, userId, from, to, types, sort, page));
    }

    @Operation(summary = "공유 사례 목록 조회")
    @GetMapping("/{teamId}/shared-cases")
    public ApiResponse<SharedCasesResponse> getSharedCases(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "taskType", required = false) Long taskTypeId,
            @RequestParam(required = false) String type) {
        return ApiResponse.success("S200", "조회 성공", teamService.getSharedCases(teamId, userId, taskTypeId, type));
    }

    @Operation(summary = "가이드 목록 조회")
    @GetMapping("/{teamId}/guides")
    public ApiResponse<GuidesResponse> getGuides(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String type) {
        return ApiResponse.success("S200", "조회 성공", teamService.getGuides(teamId, userId, type));
    }

    @Operation(summary = "분석 대상·평가 기준 조회")
    @GetMapping("/{teamId}/settings")
    public ApiResponse<TeamSettingsResponse> getSettings(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success("S200", "조회 성공", teamService.getSettings(teamId, userId));
    }

    @Operation(summary = "분석 대상 설정")
    @PutMapping("/{teamId}/settings")
    public ApiResponse<TeamSettingsUpdateResponse> updateSettings(
            @PathVariable Long teamId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TeamSettingsUpdateRequest request) {
        return ApiResponse.success("S200", "설정 저장 성공", teamService.updateSettings(teamId, userId, request));
    }
}
