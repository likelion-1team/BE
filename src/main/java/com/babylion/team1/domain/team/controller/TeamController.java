package com.babylion.team1.domain.team.controller;

import com.babylion.team1.domain.team.dto.request.TeamCreateRequest;
import com.babylion.team1.domain.team.dto.request.TeamMemberAddRequest;
import com.babylion.team1.domain.team.dto.response.MyTeamResponse;
import com.babylion.team1.domain.team.dto.response.TeamCreateResponse;
import com.babylion.team1.domain.team.dto.response.TeamMemberAddResponse;
import com.babylion.team1.domain.team.service.TeamService;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
