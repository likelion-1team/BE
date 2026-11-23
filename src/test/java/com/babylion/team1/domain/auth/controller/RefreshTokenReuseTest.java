package com.babylion.team1.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.auth.repository.AuthSessionRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 재사용을 감지하면 예외로 응답하면서도 세션 폐기는 커밋되어야 한다.
 * 실제 커밋·롤백을 확인해야 하므로 테스트에 트랜잭션을 걸지 않고 데이터를 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenReuseTest {

    private static final String REFRESH_URL = "/api/v1/auth/refresh";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User user;

    @BeforeEach
    void saveUser() {
        user = userRepository.save(User.builder()
                .name("홍길동")
                .email("reuse-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .build());
    }

    /** 세션은 ON DELETE CASCADE로 함께 지워진다. */
    @AfterEach
    void deleteUser() {
        userRepository.deleteById(user.getId());
    }

    @Test
    @DisplayName("이미 쓴 리프레시 토큰이 다시 들어오면 세션 전체를 폐기해 새 토큰도 쓸 수 없다")
    void reuseRevokesWholeSession() throws Exception {
        MockHttpServletResponse login = login();
        MockHttpServletResponse otherLogin = login();
        MockHttpServletResponse renewed = refresh(refreshToken(login));
        assertThat(renewed.getStatus()).isEqualTo(200);

        mockMvc.perform(post(REFRESH_URL).cookie(new Cookie("refreshToken", refreshToken(login))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));

        assertThat(refresh(refreshToken(renewed)).getStatus()).isEqualTo(401);
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(renewed)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        String sessionId = jwtProvider.getSessionId(jwtProvider.parseClaims(accessToken(login)));
        assertThat(authSessionRepository.findById(sessionId).orElseThrow().isRevoked()).isTrue();

        // 다른 기기의 세션은 영향을 받지 않는다.
        assertThat(refresh(refreshToken(otherLogin)).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("같은 리프레시 토큰으로 동시에 재발급하면 하나만 성공하고 세션은 폐기된다")
    void concurrentRefresh() throws Exception {
        String refreshToken = refreshToken(login());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier start = new CyclicBarrier(2);
            Callable<Integer> refresh = () -> {
                start.await(5, TimeUnit.SECONDS);
                return refresh(refreshToken).getStatus();
            };
            List<Future<Integer>> results = executor.invokeAll(List.of(refresh, refresh), 20, TimeUnit.SECONDS);

            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 401);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        String sessionId = jwtProvider.getSessionIdFromRefreshToken(refreshToken);
        assertThat(authSessionRepository.findById(sessionId).orElseThrow().isRevoked()).isTrue();
    }

    private MockHttpServletResponse login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123"}
                                """.formatted(user.getEmail())))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private MockHttpServletResponse refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post(REFRESH_URL).cookie(new Cookie("refreshToken", refreshToken)))
                .andReturn().getResponse();
    }

    private String refreshToken(MockHttpServletResponse response) {
        return response.getCookie("refreshToken").getValue();
    }

    private String accessToken(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString()).get("data").get("accessToken").asString();
    }
}
