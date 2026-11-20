package com.babylion.team1.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("회원가입에 성공하면 201과 userId를 응답하고 비밀번호는 BCrypt 해시로 저장된다")
    void signupSuccess() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("홍길동", "signup-success@test.com", "password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S201"))
                .andExpect(jsonPath("$.message").value("회원가입 성공"))
                .andExpect(jsonPath("$.data.userId").isNumber());

        User saved = userRepository.findByEmail("signup-success@test.com").orElseThrow();
        assertThat(saved.getName()).isEqualTo("홍길동");
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 409 EMAIL_ALREADY_EXISTS로 응답한다")
    void signupDuplicateEmail() throws Exception {
        userRepository.save(User.builder()
                .name("기존회원")
                .email("dup@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .build());

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("홍길동", "dup@test.com", "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 400 INVALID_PASSWORD_FORMAT으로 응답한다")
    void signupPasswordTooShort() throws Exception {
        expectInvalidPassword("abc123");
    }

    @Test
    @DisplayName("비밀번호에 숫자가 없으면 400 INVALID_PASSWORD_FORMAT으로 응답한다")
    void signupPasswordWithoutDigit() throws Exception {
        expectInvalidPassword("abcdefgh");
    }

    @Test
    @DisplayName("비밀번호에 영문이 없으면 400 INVALID_PASSWORD_FORMAT으로 응답한다")
    void signupPasswordWithoutLetter() throws Exception {
        expectInvalidPassword("12345678");
    }

    @Test
    @DisplayName("필수 값이 비어 있으면 400 INVALID_REQUEST로 응답한다")
    void signupMissingField() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "missing@test.com", "password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("이름이 50자를 넘으면 400 INVALID_REQUEST로 응답한다")
    void signupNameTooLong() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("가".repeat(51), "longname@test.com", "password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    private void expectInvalidPassword(String password) throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("홍길동", "invalid-pw@test.com", password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_FORMAT"));

        assertThat(userRepository.existsByEmail("invalid-pw@test.com")).isFalse();
    }

    private String body(String name, String email, String password) {
        return """
                {"name": "%s", "email": "%s", "password": "%s"}
                """.formatted(name, email, password);
    }
}
