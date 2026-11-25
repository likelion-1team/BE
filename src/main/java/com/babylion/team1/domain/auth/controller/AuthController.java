package com.babylion.team1.domain.auth.controller;

import com.babylion.team1.domain.auth.dto.AuthResult;
import com.babylion.team1.domain.auth.dto.request.EmailCheckRequest;
import com.babylion.team1.domain.auth.dto.request.LoginRequest;
import com.babylion.team1.domain.auth.dto.request.SignupRequest;
import com.babylion.team1.domain.auth.dto.response.EmailCheckResponse;
import com.babylion.team1.domain.auth.dto.response.LoginResponse;
import com.babylion.team1.domain.auth.dto.response.SignupResponse;
import com.babylion.team1.domain.auth.dto.response.TokenRefreshResponse;
import com.babylion.team1.domain.auth.service.AuthService;
import com.babylion.team1.domain.auth.service.EmailCheckRateLimiter;
import com.babylion.team1.global.config.SwaggerConfig;
import com.babylion.team1.global.jwt.JwtAuthenticationFilter;
import com.babylion.team1.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
    private final EmailCheckRateLimiter emailCheckRateLimiter;
    private final RefreshTokenCookie refreshTokenCookie;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("S201", "회원가입 성공", response));
    }

    @GetMapping("/email-check")
    public ResponseEntity<ApiResponse<EmailCheckResponse>> checkEmail(
            @ParameterObject @Valid @ModelAttribute EmailCheckRequest request, HttpServletRequest httpRequest) {
        emailCheckRateLimiter.check(httpRequest.getRemoteAddr());
        EmailCheckResponse response = authService.checkEmail(request);
        return ResponseEntity.ok(ApiResponse.success("S200", "조회 성공", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResult<LoginResponse> result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.issue(result.refreshToken()))
                .body(ApiResponse.success("S200", "로그인 성공", result.response()));
    }

    /** 리프레시 토큰은 브라우저가 쿠키로 자동 전송하므로 요청 본문은 쓰지 않는다. */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @Parameter(hidden = true)
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        AuthResult<TokenRefreshResponse> result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.issue(result.refreshToken()))
                .body(ApiResponse.success("S200", "토큰 재발급 성공", result.response()));
    }

    /**
     * 이미 폐기된 세션의 토큰으로도 204를 응답해야 하므로 시큐리티에서는 허용하고 서비스에서 토큰을 확인한다.
     * 토큰을 보내는 API이므로 클래스의 @SecurityRequirements와 달리 Swagger 토큰(자물쇠)을 표시한다.
     */
    @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true)
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        authService.logout(JwtAuthenticationFilter.resolveToken(httpRequest), refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.expire())
                .build();
    }
}
