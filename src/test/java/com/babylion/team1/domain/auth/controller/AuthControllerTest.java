package com.babylion.team1.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.auth.entity.AuthSession;
import com.babylion.team1.domain.auth.repository.AuthSessionRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";
    private static final String EMAIL_CHECK_URL = "/api/v1/auth/email-check";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String REFRESH_URL = "/api/v1/auth/refresh";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";
    private static final String ME_URL = "/api/v1/users/me";
    private static final String REFRESH_COOKIE = "refreshToken";
    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

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

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.rate-limit.email-check.max-requests}")
    private int emailCheckMaxRequests;

    // ---------- 회원가입 ----------

    @Test
    @DisplayName("회원가입에 성공하면 201과 사용자 정보를 응답하고 비밀번호는 BCrypt 해시로 저장된다")
    void signupSuccess() throws Exception {
        signup("홍길동", "signup-success@test.com", "password123", 201)
                .andExpect(jsonPath("$.code").value("S201"))
                .andExpect(jsonPath("$.message").value("회원가입 성공"))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.email").value("signup-success@test.com"));

        User saved = userRepository.findByEmail("signup-success@test.com").orElseThrow();
        assertThat(saved.getName()).isEqualTo("홍길동");
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("이메일은 소문자로 바꾸고 앞뒤 공백을 지운 뒤 저장한다")
    void signupNormalizesEmail() throws Exception {
        signup("홍길동", "  Normalize@Test.COM ", "password123", 201)
                .andExpect(jsonPath("$.data.email").value("normalize@test.com"));

        assertThat(userRepository.findByEmail("normalize@test.com").orElseThrow().getEmail())
                .isEqualTo("normalize@test.com");
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 대소문자·공백이 달라도 409 EMAIL_IN_USE로 응답한다")
    void signupDuplicateEmail() throws Exception {
        saveUser("기존회원", "dup@test.com", "password123");

        signup("홍길동", "dup@test.com", "password123", 409)
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
        signup("홍길동", " DUP@test.com ", "password123", 409)
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc1234", "abcdefgh", "12345678", ""})
    @DisplayName("비밀번호가 8자 미만이거나 영문·숫자를 모두 포함하지 않으면 422 VALIDATION_FAILED로 응답한다")
    void signupInvalidPassword(String password) throws Exception {
        signup("홍길동", "invalid-pw@test.com", password, 422)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        assertThat(userRepository.existsByEmail("invalid-pw@test.com")).isFalse();
    }

    @Test
    @DisplayName("비밀번호가 128자를 넘으면 422 VALIDATION_FAILED로 응답한다")
    void signupPasswordTooLong() throws Exception {
        signup("홍길동", "long-pw@test.com", "a1".repeat(64) + "a", 422)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    @DisplayName("BCrypt 한계(72바이트)를 넘는 128자 비밀번호로도 가입·로그인할 수 있고 뒷부분이 다르면 실패한다")
    void signupAndLoginWithMaxLengthPassword() throws Exception {
        String password = "a1".repeat(64);
        signup("홍길동", "max-pw@test.com", password, 201);

        login("max-pw@test.com", password);
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("max-pw@test.com", password.substring(0, 127) + "2")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("필수 값이 비어 있으면 422 VALIDATION_FAILED로 응답한다")
    void signupMissingField() throws Exception {
        signup("", "missing@test.com", "password123", 422)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("이름은 50자까지 허용하고 넘으면 422 VALIDATION_FAILED로 응답한다")
    void signupNameLength() throws Exception {
        signup("가".repeat(50), "name50@test.com", "password123", 201);
        signup("가".repeat(51), "name51@test.com", "password123", 422)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 422 VALIDATION_FAILED로 응답한다")
    void signupInvalidEmail() throws Exception {
        signup("홍길동", "not-an-email", "password123", 422)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    // ---------- 이메일 중복 확인 ----------

    @Test
    @DisplayName("가입되지 않은 이메일이면 200과 available true를 응답한다")
    void emailCheckAvailable() throws Exception {
        mockMvc.perform(get(EMAIL_CHECK_URL).param("email", "new-user@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 대소문자·공백이 달라도 available false를 응답한다")
    void emailCheckAlreadyExists() throws Exception {
        saveUser("기존회원", "taken@test.com", "password123");

        for (String email : new String[]{"taken@test.com", " TAKEN@test.com "}) {
            mockMvc.perform(get(EMAIL_CHECK_URL).param("email", email))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("S200"))
                    .andExpect(jsonPath("$.data.available").value(false));
        }
    }

    @Test
    @DisplayName("이메일이 없거나 비었거나 형식이 틀리면 422 VALIDATION_FAILED로 응답한다")
    void emailCheckInvalidEmail() throws Exception {
        mockMvc.perform(get(EMAIL_CHECK_URL))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        for (String email : new String[]{"", "not-an-email"}) {
            mockMvc.perform(get(EMAIL_CHECK_URL).param("email", email))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    @DisplayName("POST 방식은 더 이상 로그인 없이 호출할 수 없다")
    void emailCheckPostNotAllowed() throws Exception {
        mockMvc.perform(post(EMAIL_CHECK_URL).param("email", "post@test.com"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("짧은 시간에 너무 많이 호출하면 429 RATE_LIMITED로 응답하고 다른 IP는 영향받지 않는다")
    void emailCheckRateLimited() throws Exception {
        for (int i = 0; i < emailCheckMaxRequests; i++) {
            emailCheckFrom("203.0.113.1", 200);
        }
        emailCheckFrom("203.0.113.1", 429)
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        emailCheckFrom("203.0.113.2", 200);
    }

    // ---------- 로그인 ----------

    @Test
    @DisplayName("로그인에 성공하면 액세스 토큰·만료 시간·사용자만 본문으로, 리프레시 토큰은 쿠키로 응답한다")
    void loginSuccess() throws Exception {
        User user = saveUser("홍길동", "login@test.com", "password123");

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login@test.com", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("로그인 성공"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.user.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.user.name").value("홍길동"))
                .andExpect(jsonPath("$.data.user.email").value("login@test.com"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn();

        Tokens tokens = tokens(result);
        assertRefreshCookieAttributes(result, "Max-Age=604800");
        assertThat(jwtProvider.parseClaims(tokens.access()).getSubject()).isEqualTo(String.valueOf(user.getId()));

        AuthSession session = session(tokens);
        assertThat(session.getUser().getId()).isEqualTo(user.getId());
        assertThat(session.isRevoked()).isFalse();
        assertThat(session.getRefreshTokenHash()).hasSize(64).isNotEqualTo(tokens.refresh());
        assertThat(session.getExpiresAt())
                .isBetween(LocalDateTime.now().plusDays(7).minusMinutes(1), LocalDateTime.now().plusDays(7));
        requestMe(tokens.access(), 200);
    }

    @Test
    @DisplayName("이메일의 대소문자·앞뒤 공백이 달라도 로그인할 수 있다")
    void loginNormalizesEmail() throws Exception {
        saveUser("홍길동", "login-normalize@test.com", "password123");

        login(" Login-Normalize@TEST.com ", "password123");
    }

    @Test
    @DisplayName("기존 방식(BCrypt)으로 저장된 비밀번호로도 로그인할 수 있다")
    void loginWithExistingBcryptHash() throws Exception {
        userRepository.saveAndFlush(User.builder()
                .name("기존회원")
                .email("legacy@test.com")
                .passwordHash(new BCryptPasswordEncoder().encode("password123"))
                .build());

        login("legacy@test.com", "password123");
    }

    @Test
    @DisplayName("다시 로그인하면 새 세션이 생기고 기존 세션은 유지되며 로그아웃은 해당 세션만 폐기한다")
    void loginCreatesSeparateSessions() throws Exception {
        saveUser("홍길동", "relogin@test.com", "password123");
        Tokens first = login("relogin@test.com", "password123");
        Tokens second = login("relogin@test.com", "password123");

        mockMvc.perform(post(LOGOUT_URL).header(HttpHeaders.AUTHORIZATION, bearer(first.access())))
                .andExpect(status().isNoContent());

        requestMe(first.access(), 401);
        requestMe(second.access(), 200);
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호는 같은 401 INVALID_CREDENTIALS로 응답하고 쿠키·세션을 만들지 않는다")
    void loginInvalidCredentials() throws Exception {
        saveUser("홍길동", "wrong-pw@test.com", "password123");
        long sessions = authSessionRepository.count();

        for (String body : new String[]{
                loginBody("nobody@test.com", "password123"), loginBody("wrong-pw@test.com", "wrongpass1")}) {
            mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
        assertThat(authSessionRepository.count()).isEqualTo(sessions);
    }

    @Test
    @DisplayName("비밀번호가 비었거나 이메일 형식이 틀리면 422 VALIDATION_FAILED로 응답한다")
    void loginValidation() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login@test.com", "")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("not-an-email", "password123")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    // ---------- CORS ----------

    @Test
    @DisplayName("허용된 Origin의 요청은 쿠키를 포함해 처리한다")
    void loginFromAllowedOrigin() throws Exception {
        saveUser("홍길동", "cors@test.com", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("cors@test.com", "password123")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("허용되지 않은 Origin의 요청은 403으로 막고 쿠키를 주지 않는다")
    void loginFromDisallowedOrigin() throws Exception {
        saveUser("홍길동", "cors-evil@test.com", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("cors-evil@test.com", "password123")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("인증이 필요한 API의 사전 요청(preflight)은 토큰 없이 통과한다")
    void preflight() throws Exception {
        mockMvc.perform(options(ME_URL)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    // ---------- 토큰 재발급 ----------

    @Test
    @DisplayName("쿠키의 리프레시 토큰으로 재발급하면 새 액세스 토큰과 교체된 쿠키를 응답한다")
    void refreshSuccess() throws Exception {
        saveUser("홍길동", "refresh@test.com", "password123");
        Tokens old = login("refresh@test.com", "password123");

        MvcResult result = mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie(old.refresh())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("토큰 재발급 성공"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn();

        Tokens renewed = tokens(result);
        assertRefreshCookieAttributes(result, "Max-Age=604800");
        assertThat(renewed.refresh()).isNotEqualTo(old.refresh());
        assertThat(session(renewed).getId()).isEqualTo(session(old).getId());
        requestMe(renewed.access(), 200);
        // 교체된 쿠키로 다시 재발급할 수 있다.
        mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie(renewed.refresh()))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("빈 JSON 본문을 함께 보내도 쿠키만 사용한다")
    void refreshIgnoresBody() throws Exception {
        saveUser("홍길동", "refresh-body@test.com", "password123");
        Tokens tokens = login("refresh-body@test.com", "password123");

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .cookie(refreshCookie(tokens.refresh())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("쿠키가 없거나 비었거나 형식이 잘못됐으면 401 SESSION_EXPIRED로 응답한다")
    void refreshWithoutUsableCookie() throws Exception {
        mockMvc.perform(post(REFRESH_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        expectRefreshFailure("");
        expectRefreshFailure("not-a-jwt");
    }

    @Test
    @DisplayName("만료·위조된 리프레시 토큰이나 액세스 토큰이면 401 SESSION_EXPIRED로 응답한다")
    void refreshWithInvalidToken() throws Exception {
        User user = saveUser("홍길동", "refresh-invalid@test.com", "password123");
        Tokens tokens = login("refresh-invalid@test.com", "password123");
        String sessionId = session(tokens).getId();
        JwtProvider expiredProvider = new JwtProvider(jwtSecret, -1000L, -1000L);
        JwtProvider otherProvider = new JwtProvider("another-secret-key-for-test-only-0123456789", 60000L, 60000L);

        expectRefreshFailure(expiredProvider.createRefreshToken(user.getId(), sessionId));
        expectRefreshFailure(otherProvider.createRefreshToken(user.getId(), sessionId));
        expectRefreshFailure(tokens.access());
        expectRefreshFailure(jwtProvider.createRefreshToken(user.getId(), "no-such-session"));
        // 세션 도입 전에 발급된 토큰
        expectRefreshFailure(jwtProvider.createRefreshToken(user.getId(), null));

        assertThat(session(tokens).isRevoked()).isFalse();
    }

    @Test
    @DisplayName("토큰이 만료 전이어도 세션이 만료됐으면 재발급과 API 호출 모두 401 SESSION_EXPIRED로 응답한다")
    void expiredSession() throws Exception {
        saveUser("홍길동", "session-expired@test.com", "password123");
        Tokens tokens = login("session-expired@test.com", "password123");
        AuthSession session = session(tokens);
        session.rotate(session.getRefreshTokenHash(), LocalDateTime.now().minusSeconds(1));
        authSessionRepository.flush();

        expectRefreshFailure(tokens.refresh());
        requestMe(tokens.access(), 401).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    // ---------- 로그아웃 ----------

    @Test
    @DisplayName("로그아웃하면 204와 삭제 쿠키를 응답하고 남은 액세스·리프레시 토큰을 모두 쓸 수 없다")
    void logoutSuccess() throws Exception {
        saveUser("홍길동", "logout@test.com", "password123");
        Tokens tokens = login("logout@test.com", "password123");

        MvcResult result = mockMvc.perform(post(LOGOUT_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.access()))
                        .cookie(refreshCookie(tokens.refresh())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        assertRefreshCookieAttributes(result, "Max-Age=0");
        assertThat(result.getResponse().getCookie(REFRESH_COOKIE).getValue()).isEmpty();
        assertThat(session(tokens).isRevoked()).isTrue();
        requestMe(tokens.access(), 401).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        expectRefreshFailure(tokens.refresh());
    }

    @Test
    @DisplayName("이미 폐기된 세션으로 다시 로그아웃해도 204로 응답한다")
    void logoutTwice() throws Exception {
        saveUser("홍길동", "logout-twice@test.com", "password123");
        Tokens tokens = login("logout-twice@test.com", "password123");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(LOGOUT_URL)
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens.access()))
                            .cookie(refreshCookie(tokens.refresh())))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @DisplayName("액세스 토큰이 만료됐거나 없어도 세션을 폐기하고 204로 응답한다")
    void logoutWithExpiredAccessTokenOrCookieOnly() throws Exception {
        User user = saveUser("홍길동", "logout-expired@test.com", "password123");
        Tokens first = login("logout-expired@test.com", "password123");
        Tokens second = login("logout-expired@test.com", "password123");
        String expiredAccess = new JwtProvider(jwtSecret, -1000L, -1000L)
                .createAccessToken(user.getId(), session(first).getId());

        mockMvc.perform(post(LOGOUT_URL).header(HttpHeaders.AUTHORIZATION, bearer(expiredAccess)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(LOGOUT_URL).cookie(refreshCookie(second.refresh())))
                .andExpect(status().isNoContent());

        assertThat(session(first).isRevoked()).isTrue();
        assertThat(session(second).isRevoked()).isTrue();
    }

    @Test
    @DisplayName("토큰 없이 로그아웃하면 401 AUTH_REQUIRED로 응답한다")
    void logoutWithoutToken() throws Exception {
        mockMvc.perform(post(LOGOUT_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("위조된 토큰이나 액세스 자리에 넣은 리프레시 토큰으로는 세션을 폐기하지 못하고 401 INVALID_TOKEN으로 응답한다")
    void logoutWithInvalidToken() throws Exception {
        User user = saveUser("홍길동", "logout-invalid@test.com", "password123");
        Tokens tokens = login("logout-invalid@test.com", "password123");
        String forged = new JwtProvider("another-secret-key-for-test-only-0123456789", 60000L, 60000L)
                .createAccessToken(user.getId(), session(tokens).getId());

        for (String token : new String[]{"not-a-jwt", forged, tokens.refresh()}) {
            mockMvc.perform(post(LOGOUT_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
        assertThat(session(tokens).isRevoked()).isFalse();
    }

    // ---------- 도우미 ----------

    private record Tokens(String access, String refresh) {
    }

    private Tokens login(String email, String password) throws Exception {
        return tokens(mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private Tokens tokens(MvcResult result) throws Exception {
        String access = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("accessToken").asString();
        return new Tokens(access, result.getResponse().getCookie(REFRESH_COOKIE).getValue());
    }

    private AuthSession session(Tokens tokens) {
        String sessionId = jwtProvider.getSessionId(jwtProvider.parseClaims(tokens.access()));
        return authSessionRepository.findById(sessionId).orElseThrow();
    }

    private void assertRefreshCookieAttributes(MvcResult result, String maxAge) {
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refreshToken=")
                .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/auth;", maxAge + ";");
    }

    private Cookie refreshCookie(String refreshToken) {
        return new Cookie(REFRESH_COOKIE, refreshToken);
    }

    private void expectRefreshFailure(String refreshToken) throws Exception {
        mockMvc.perform(post(REFRESH_URL).cookie(refreshCookie(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private ResultActions requestMe(String accessToken, int expectedStatus)
            throws Exception {
        return mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().is(expectedStatus));
    }

    private ResultActions emailCheckFrom(String ip, int expectedStatus)
            throws Exception {
        return mockMvc.perform(get(EMAIL_CHECK_URL)
                        .param("email", "rate-limit@test.com")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        }))
                .andExpect(status().is(expectedStatus));
    }

    private ResultActions signup(String name, String email, String password,
                                 int expectedStatus) throws Exception {
        return mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "email": "%s", "password": "%s"}
                                """.formatted(name, email, password)))
                .andExpect(status().is(expectedStatus));
    }

    private User saveUser(String name, String email, String password) {
        return userRepository.saveAndFlush(User.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .build());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
