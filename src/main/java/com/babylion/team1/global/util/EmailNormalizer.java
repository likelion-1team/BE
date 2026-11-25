package com.babylion.team1.global.util;

import java.util.Locale;

/** 같은 이메일이 대소문자·공백 차이로 다른 계정이 되지 않도록 저장·조회 전에 형태를 통일한다. */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /** 앞뒤 공백을 지우고 소문자로 바꾼다. null은 그대로 두어 @NotBlank 검증에 맡긴다. */
    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
