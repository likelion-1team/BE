package com.babylion.team1.domain.auth.controller;

import com.babylion.team1.domain.auth.dto.request.EmailCheckRequest;
import com.babylion.team1.domain.auth.dto.request.LoginRequest;
import com.babylion.team1.domain.auth.dto.request.SignupRequest;
import com.babylion.team1.domain.auth.dto.request.TokenRefreshRequest;
import com.babylion.team1.domain.auth.dto.response.EmailCheckResponse;
import com.babylion.team1.domain.auth.dto.response.LoginResponse;
import com.babylion.team1.domain.auth.dto.response.SignupResponse;
import com.babylion.team1.domain.auth.dto.response.TokenRefreshResponse;
import com.babylion.team1.domain.auth.service.AuthService;
import com.babylion.team1.global.config.SwaggerConfig;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 없이 호출하는 API이므로 Swagger에서 토큰(자물쇠) 표시를 뺀다. */
@SecurityRequirements
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("S201", "회원가입 성공", response));
    }

    @PostMapping("/email-check")
    public ResponseEntity<ApiResponse<EmailCheckResponse>> checkEmail(@Valid @RequestBody EmailCheckRequest request) {
        EmailCheckResponse response = authService.checkEmail(request);
        return ResponseEntity.ok(ApiResponse.success("S200", "이메일 중복 확인 성공", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("S200", "로그인 성공", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        TokenRefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success("S200", "토큰 재발급 성공", response));
    }

    /** 인증이 필요한 API이므로 클래스의 @SecurityRequirements와 달리 Swagger 토큰(자물쇠)을 표시한다. */
    @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}
