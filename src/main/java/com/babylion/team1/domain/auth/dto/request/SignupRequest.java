package com.babylion.team1.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청.
 * 비밀번호 형식(영문+숫자 포함 8자 이상)은 INVALID_PASSWORD_FORMAT 코드로 응답해야 하므로
 * 여기서는 필수 여부만 검사하고, 형식 검사는 AuthService에서 한다.
 */
public record SignupRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
