package com.babylion.team1.domain.auth.dto.request;

import com.babylion.team1.global.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이메일 중복 확인 쿼리 파라미터. 가입 때와 같은 방식으로 정규화해 비교한다. */
public record EmailCheckRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email
) {

    public EmailCheckRequest {
        email = EmailNormalizer.normalize(email);
    }
}
