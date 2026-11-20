package com.babylion.team1.global.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(JwtAuthenticationFilterTest.TestAuthController.class)
class JwtAuthenticationFilterTest {

    private static final String PROTECTED_URL = "/test/jwt/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Value("${jwt.secret}")
    private String secret;

    @Test
    @DisplayName("유효한 액세스 토큰이면 인증되고 principal로 userId를 꺼낼 수 있다")
    void validAccessToken() throws Exception {
        String accessToken = jwtProvider.createAccessToken(7L);

        mockMvc.perform(get(PROTECTED_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    @DisplayName("토큰 없이 인증이 필요한 API를 호출하면 401 UNAUTHORIZED로 응답한다")
    void noToken() throws Exception {
        mockMvc.perform(get(PROTECTED_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Bearer 형식이 아닌 Authorization 헤더는 토큰이 없는 것으로 보고 401 UNAUTHORIZED로 응답한다")
    void notBearerHeader() throws Exception {
        String accessToken = jwtProvider.createAccessToken(7L);

        mockMvc.perform(get(PROTECTED_URL).header(HttpHeaders.AUTHORIZATION, accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("만료된 액세스 토큰이면 401 EXPIRED_TOKEN으로 응답한다")
    void expiredToken() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(secret, -1000L, -1000L);
        String expiredToken = expiredProvider.createAccessToken(7L);

        mockMvc.perform(get(PROTECTED_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰이면 401 INVALID_TOKEN으로 응답한다")
    void tokenSignedWithOtherKey() throws Exception {
        JwtProvider otherProvider = new JwtProvider("another-secret-key-for-test-only-0123456789", 60000L, 60000L);
        String forgedToken = otherProvider.createAccessToken(7L);

        mockMvc.perform(get(PROTECTED_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("형식이 깨진 토큰이면 401 INVALID_TOKEN으로 응답한다")
    void malformedToken() throws Exception {
        mockMvc.perform(get(PROTECTED_URL).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("리프레시 토큰으로 인증이 필요한 API를 호출하면 401 INVALID_TOKEN으로 응답한다")
    void refreshTokenAsAccessToken() throws Exception {
        String refreshToken = jwtProvider.createRefreshToken(7L);

        mockMvc.perform(get(PROTECTED_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("인증 없이 허용된 API는 잘못된 토큰을 보내도 그대로 처리된다")
    void permitAllIgnoresInvalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "filter-permit@test.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"));
    }

    /** 인증이 필요한 경로를 흉내 내는 테스트 전용 컨트롤러 */
    @RestController
    static class TestAuthController {

        @GetMapping(PROTECTED_URL)
        public MeResponse me(@AuthenticationPrincipal Long userId) {
            return new MeResponse(userId);
        }

        record MeResponse(Long userId) {
        }
    }
}
