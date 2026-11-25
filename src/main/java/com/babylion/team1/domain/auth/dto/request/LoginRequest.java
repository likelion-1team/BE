package com.babylion.team1.domain.auth.dto.request;

import com.babylion.team1.global.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 로그인 요청. 이메일은 가입 때와 같은 방식으로 정규화해 비교한다. */
public record LoginRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {

    public LoginRequest {
        email = EmailNormalizer.normalize(email);
    }
}
