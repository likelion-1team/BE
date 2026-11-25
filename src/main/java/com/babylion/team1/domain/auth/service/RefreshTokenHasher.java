package com.babylion.team1.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** DB가 유출돼도 리프레시 토큰을 그대로 쓸 수 없도록 해시로 저장한다. */
final class RefreshTokenHasher {

    private RefreshTokenHasher() {
    }

    static String hash(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
