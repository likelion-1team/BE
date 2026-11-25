package com.babylion.team1.global.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.auth.repository.AuthSessionRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.support.TestAccessTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(JwtAuthenticationFilterTest.TestAuthController.class)
@Transactional
class JwtAuthenticationFilterTest {

    private static final String PROTECTED_URL = "/test/jwt/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TestAccessTokens accessTokens;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Value("${jwt.secret}")
    private String secret;

    @Test
    @DisplayName("유효한 액세스 토큰이면 인증되고 principal로 userId를 꺼낼 수 있다")
    void validAccessToken() throws Exception {
        Long userId = saveUser();
        String accessToken = accessTokens.create(userId);

        mockMvc.perform(get(PROTECTED_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    @DisplayName("세션이 폐기됐으면 만료 전 액세스 토큰이어도 401 SESSION_EXPIRED로 응답한다")
    void revokedSession() throws Exception {
        Long userId = saveUser();
        String sessionId = accessTokens.createSession(userId);
        authSessionRepository.findById(sessionId).orElseThrow().revoke();
        authSessionRepository.flush();

        expectSessionExpired(jwtProvider.createAccessToken(userId, sessionId));
    }

    @Test
    @DisplayName("존재하지 않는 세션, 다른 사용자의 세션, 세션 ID가 없는 토큰이면 401 SESSION_EXPIRED로 응답한다")
    void unknownSession() throws Exception {
        Long userId = saveUser();
        String othersSessionId = accessTokens.createSession(saveUser());

        expectSessionExpired(jwtProvider.createAccessToken(userId, "no-such-session"));
        expectSessionExpired(jwtProvider.createAccessToken(userId, othersSessionId));
        expectSessionExpired(jwtProvider.createAccessToken(userId, null));
    }

    @Test
    @DisplayName("토큰 없이 인증이 필요한 API를 호출하면 401 AUTH_REQUIRED로 응답한다")
    void noToken() throws Exception {
        mockMvc.perform(get(PROTECTED_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Bearer 형식이 아닌 Authorization 헤더는 토큰이 없는 것으로 보고 401 AUTH_REQUIRED로 응답한다")
    void notBearerHeader() throws Exception {
        String accessToken = accessTokens.create(saveUser());

        mockMvc.perform(get(PROTECTED_URL).header(HttpHeaders.AUTHORIZATION, accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("만료된 액세스 토큰이면 401 SESSION_EXPIRED으로 응답한다")
    void expiredToken() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(secret, -1000L, -1000L);
        String expiredToken = expiredProvider.createAccessToken(7L, "session");

        mockMvc.perform(get(PROTECTED_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰이면 401 INVALID_TOKEN으로 응답한다")
    void tokenSignedWithOtherKey() throws Exception {
        JwtProvider otherProvider = new JwtProvider("another-secret-key-for-test-only-0123456789", 60000L, 60000L);
        String forgedToken = otherProvider.createAccessToken(7L, "session");

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
        String refreshToken = jwtProvider.createRefreshToken(7L, "session");

        mockMvc.perform(get(PROTECTED_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("인증 없이 허용된 API는 잘못된 토큰을 보내도 그대로 처리된다")
    void permitAllIgnoresInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/email-check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                        .param("email", "filter-permit@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"));
    }

    private Long saveUser() {
        return userRepository.saveAndFlush(User.builder()
                .name("홍길동")
                .email("filter-" + UUID.randomUUID() + "@test.com")
                .passwordHash("hashed-password")
                .build()).getId();
    }

    private void expectSessionExpired(String accessToken) throws Exception {
        mockMvc.perform(get(PROTECTED_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
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
