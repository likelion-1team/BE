package com.babylion.team1.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.auth.repository.AuthSessionRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.jwt.JwtProvider;
import com.babylion.team1.support.TestAccessTokens;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    private static final String ME_URL = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private TestAccessTokens accessTokens;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    @DisplayName("내 정보를 조회하면 200과 userId, name, email을 응답한다")
    void getMeSuccess() throws Exception {
        User user = saveUser("홍길동", "me@test.com");

        mockMvc.perform(get(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessTokens.create(user.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.email").value("me@test.com"))
                .andExpect(jsonPath("$.data.role").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401 AUTH_REQUIRED로 응답한다")
    void getMeWithoutToken() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("만료된 액세스 토큰으로 요청하면 401 SESSION_EXPIRED으로 응답한다")
    void getMeWithExpiredToken() throws Exception {
        User user = saveUser("홍길동", "me-expired@test.com");
        JwtProvider expiredProvider = new JwtProvider(jwtSecret, -1000L, -1000L);

        mockMvc.perform(get(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredProvider.createAccessToken(user.getId(), accessTokens.createSession(user.getId())))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("형식이 잘못된 토큰으로 요청하면 401 INVALID_TOKEN으로 응답한다")
    void getMeWithMalformedToken() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰으로 요청하면 401 INVALID_TOKEN으로 응답한다")
    void getMeWithForgedToken() throws Exception {
        User user = saveUser("홍길동", "me-forged@test.com");
        JwtProvider otherProvider = new JwtProvider("other-secret-key-for-forged-token-test-0123456789", 60000L, 60000L);

        mockMvc.perform(get(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherProvider.createAccessToken(user.getId(), accessTokens.createSession(user.getId())))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("리프레시 토큰으로 요청하면 401 INVALID_TOKEN으로 응답한다")
    void getMeWithRefreshToken() throws Exception {
        User user = saveUser("홍길동", "me-refresh@test.com");

        mockMvc.perform(get(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.createRefreshToken(user.getId(), accessTokens.createSession(user.getId())))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("세션이 폐기됐으면 만료 전 액세스 토큰이어도 401 SESSION_EXPIRED로 응답한다")
    void getMeWithRevokedSession() throws Exception {
        User user = saveUser("홍길동", "me-revoked@test.com");
        String sessionId = accessTokens.createSession(user.getId());
        authSessionRepository.findById(sessionId).orElseThrow().revoke();
        authSessionRepository.flush();

        mockMvc.perform(get(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.createAccessToken(user.getId(), sessionId))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("사용자가 삭제되면 세션도 함께 사라져 401 SESSION_EXPIRED로 응답한다")
    void getMeUserDeleted() throws Exception {
        User user = saveUser("홍길동", "me-deleted@test.com");
        String accessToken = accessTokens.create(user.getId());
        // 세션은 DB의 ON DELETE CASCADE로 지워지므로 영속성 컨텍스트를 비우고 확인한다.
        entityManager.clear();
        userRepository.deleteById(user.getId());
        userRepository.flush();

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    private User saveUser(String name, String email) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .passwordHash("hashed-password")
                .build());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
