package com.babylion.team1.global.jwt;

import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 액세스·리프레시 토큰(JWT) 발급 및 파싱.
 * 두 토큰 모두 로그인 세션 ID(sid)를 담아, 세션이 폐기되면 함께 무효가 된다.
 * 만료 시간(ms)과 비밀 키는 application.yaml의 jwt.* 값으로 설정한다.
 */
@Component
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String SESSION_ID_CLAIM = "sid";
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

    public String createAccessToken(Long userId, String sessionId) {
        return createToken(userId, sessionId, ACCESS_TYPE, accessTokenExpiration);
    }

    public String createRefreshToken(Long userId, String sessionId) {
        return createToken(userId, sessionId, REFRESH_TYPE, refreshTokenExpiration);
    }

    /** 서명·만료를 검증한 뒤 클레임을 반환한다. 유효하지 않으면 JwtException을 던진다. */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 서명만 검증하고 만료는 무시한다(로그아웃용). 우리가 발급한 토큰이 아니면 빈 값을 반환한다. */
    public Optional<Claims> parseIgnoringExpiration(String token) {
        try {
            return Optional.of(parseClaims(token));
        } catch (ExpiredJwtException e) {
            // 만료는 서명 검증을 통과한 뒤에 검사하므로 이 클레임은 믿을 수 있다.
            return Optional.of(e.getClaims());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isAccessToken(Claims claims) {
        return ACCESS_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return REFRESH_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    /** 세션 도입 전에 발급된 토큰에는 세션 ID가 없으므로 null일 수 있다. */
    public String getSessionId(Claims claims) {
        return claims.get(SESSION_ID_CLAIM, String.class);
    }

    /** 리프레시 토큰을 검증하고 세션 ID를 반환한다. 없거나 만료·위조됐거나 리프레시 토큰이 아니면 모두 SESSION_EXPIRED 예외를 던진다. */
    public String getSessionIdFromRefreshToken(String refreshToken) {
        try {
            Claims claims = parseClaims(refreshToken);
            String sessionId = getSessionId(claims);
            if (isRefreshToken(claims) && sessionId != null) {
                return sessionId;
            }
        } catch (JwtException | IllegalArgumentException e) {
            // 만료, 서명 불일치, 형식 오류, 쿠키 없음 등. 모두 다시 로그인해야 하므로 구분하지 않는다.
        }
        throw new CustomException(ErrorCode.SESSION_EXPIRED);
    }

    public long getAccessTokenExpirationSeconds() {
        return Duration.ofMillis(accessTokenExpiration).toSeconds();
    }

    public long getRefreshTokenExpirationSeconds() {
        return Duration.ofMillis(refreshTokenExpiration).toSeconds();
    }

    private String createToken(Long userId, String sessionId, String type, long expirationMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_TYPE_CLAIM, type)
                .claim(SESSION_ID_CLAIM, sessionId)
                // 같은 밀리초에 재발급해도 토큰 값이 달라지도록 jti를 넣는다.
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(secretKey)
                .compact();
    }
}
