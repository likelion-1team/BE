package com.babylion.team1.global.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt는 72바이트까지만 처리할 수 있어(초과하면 예외) 128자 비밀번호를 그대로 해시할 수 없다.
 * 72바이트를 넘는 비밀번호만 SHA-256으로 줄인 뒤 BCrypt를 적용한다.
 * 72바이트 이하는 기존과 같으므로 이미 저장된 해시도 그대로 검증된다.
 */
class LongPasswordEncoder implements PasswordEncoder {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final PasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(shorten(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return bcrypt.matches(shorten(rawPassword), encodedPassword);
    }

    private CharSequence shorten(CharSequence rawPassword) {
        byte[] bytes = rawPassword.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= BCRYPT_MAX_BYTES) {
            return rawPassword;
        }
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
