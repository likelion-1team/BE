package com.babylion.team1.domain.auth.dto.request;

import com.babylion.team1.global.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 회원가입 요청. 이메일은 검증 전에 정규화(소문자·앞뒤 공백 제거)한다. */
public record SignupRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,128}$",
                message = "비밀번호는 영문과 숫자를 포함해 8~128자여야 합니다.")
        String password
) {

    public SignupRequest {
        email = EmailNormalizer.normalize(email);
    }
}
