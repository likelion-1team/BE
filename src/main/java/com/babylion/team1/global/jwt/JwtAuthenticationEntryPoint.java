package com.babylion.team1.global.jwt;

import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증이 필요한 경로에 인증 없이 접근했을 때 공통 응답 형식으로 401을 내려준다.
 * 시큐리티 필터 단계의 예외는 GlobalExceptionHandler까지 오지 않으므로 여기서 직접 응답을 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE) instanceof ErrorCode code
                ? code
                : ErrorCode.UNAUTHORIZED;
        log.warn("[Authentication] {} - {} {}", errorCode.getCode(), request.getMethod(), request.getRequestURI());

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(errorCode.getCode(), errorCode.getMessage(), null));
    }
}
