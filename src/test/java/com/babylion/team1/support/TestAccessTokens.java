package com.babylion.team1.support;

import com.babylion.team1.domain.auth.entity.AuthSession;
import com.babylion.team1.domain.auth.repository.AuthSessionRepository;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.jwt.JwtProvider;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 로그인 API를 거치지 않고 인증이 필요한 API를 호출하기 위한 테스트용 액세스 토큰 발급 도구.
 * 액세스 토큰은 유효한 세션이 있어야 인증되므로 세션을 함께 저장한다.
 * 컴포넌트 스캔으로 모든 @SpringBootTest에 등록된다(@Import를 쓰면 컨텍스트가 더 생기므로 쓰지 않는다).
 */
@Component
public class TestAccessTokens {

    private final JwtProvider jwtProvider;
    private final AuthSessionRepository authSessionRepository;
    private final UserRepository userRepository;

    public TestAccessTokens(JwtProvider jwtProvider, AuthSessionRepository authSessionRepository,
                            UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.authSessionRepository = authSessionRepository;
        this.userRepository = userRepository;
    }

    public String create(Long userId) {
        return jwtProvider.createAccessToken(userId, createSession(userId));
    }

    /** 유효한 세션을 저장하고 세션 ID를 반환한다. */
    public String createSession(Long userId) {
        String sessionId = UUID.randomUUID().toString();
        authSessionRepository.saveAndFlush(AuthSession.builder()
                .id(sessionId)
                .user(userRepository.getReferenceById(userId))
                .refreshTokenHash("test-refresh-token-hash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build());
        return sessionId;
    }
}
