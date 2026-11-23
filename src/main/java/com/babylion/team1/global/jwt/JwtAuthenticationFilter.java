package com.babylion.team1.global.jwt;

import com.babylion.team1.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {accessToken} 헤더를 검증해 인증 정보를 SecurityContext에 넣는다.
 * principal은 userId(Long)이며, 컨트롤러에서 @AuthenticationPrincipal Long userId로 꺼낸다.
 *
 * 토큰이 없거나 유효하지 않아도 여기서 바로 응답하지 않는다.
 * permitAll 경로는 그대로 통과시키고, 인증이 필요한 경로만 JwtAuthenticationEntryPoint가 401로 응답한다.
 * 실패 원인은 request attribute로 넘겨 응답 코드(INVALID_TOKEN, EXPIRED_TOKEN)를 구분한다.
 *
 * SecurityConfig에서 직접 생성해 등록한다. @Component로 만들면 서블릿 필터로도 한 번 더 등록되므로 붙이지 않는다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".errorCode";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            authenticate(request, token);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            Claims claims = jwtProvider.parseClaims(token);
            // 리프레시 토큰으로 일반 API를 호출하는 것은 막는다.
            if (!jwtProvider.isAccessToken(claims)) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.INVALID_TOKEN);
                return;
            }
            Long userId = Long.valueOf(claims.getSubject());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException e) {
            request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치, 형식 오류, subject가 숫자가 아닌 경우 등
            request.setAttribute(ERROR_CODE_ATTRIBUTE, ErrorCode.INVALID_TOKEN);
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
