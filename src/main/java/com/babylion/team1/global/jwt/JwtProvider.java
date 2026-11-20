package com.babylion.team1.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 액세스·리프레시 토큰(JWT) 발급 및 파싱.
 * 만료 시간(ms)과 비밀 키는 application.yaml의 jwt.* 값으로 설정한다.
 */
@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        // HS256은 256비트(32바이트) 이상의 키가 필요하며, 짧으면 Keys.hmacShaKeyFor에서 예외가 발생한다.
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, ACCESS_TYPE, accessTokenExpiration);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, REFRESH_TYPE, refreshTokenExpiration);
    }

    /** 서명·만료를 검증한 뒤 클레임을 반환한다. 유효하지 않으면 JwtException을 던진다. */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return ACCESS_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return REFRESH_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    private String createToken(Long userId, String type, long expirationMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, type)
                // 같은 밀리초에 재발급해도 토큰 값이 달라지도록 jti를 넣는다.
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(secretKey)
                .compact();
    }
}
