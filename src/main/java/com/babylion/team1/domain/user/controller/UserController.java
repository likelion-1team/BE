package com.babylion.team1.domain.user.controller;

import com.babylion.team1.domain.user.dto.response.UserMeResponse;
import com.babylion.team1.domain.user.service.UserService;
import com.babylion.team1.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    /** principal은 JwtAuthenticationFilter가 넣어 둔 userId(Long)이다. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> getMe(@AuthenticationPrincipal Long userId) {
        UserMeResponse response = userService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }
}
