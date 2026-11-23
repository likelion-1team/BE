package com.babylion.team1.domain.auth.service;

import com.babylion.team1.domain.auth.dto.AuthResult;
import com.babylion.team1.domain.auth.dto.request.EmailCheckRequest;
import com.babylion.team1.domain.auth.dto.request.LoginRequest;
import com.babylion.team1.domain.auth.dto.request.SignupRequest;
import com.babylion.team1.domain.auth.dto.response.EmailCheckResponse;
import com.babylion.team1.domain.auth.dto.response.LoginResponse;
import com.babylion.team1.domain.auth.dto.response.SignupResponse;
import com.babylion.team1.domain.auth.dto.response.TokenRefreshResponse;
import com.babylion.team1.domain.auth.entity.AuthSession;
import com.babylion.team1.domain.auth.repository.AuthSessionRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.global.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(ErrorCode.EMAIL_IN_USE);
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        try {
            // 동시 가입 요청으로 exists 검사를 통과한 경우 unique 제약에서 걸리므로 즉시 flush해서 409로 변환한다.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.EMAIL_IN_USE);
        }

        return SignupResponse.from(user);
    }

    public EmailCheckResponse checkEmail(EmailCheckRequest request) {
        boolean exists = userRepository.existsByEmail(request.email());
        return new EmailCheckResponse(!exists);
    }

    /** 로그인할 때마다 새 세션을 만든다. 다른 기기에서 로그인한 기존 세션은 그대로 유지된다. */
    @Transactional
    public AuthResult<LoginResponse> login(LoginRequest request) {
        // 없는 이메일과 틀린 비밀번호를 구분하지 않고 같은 에러로 응답한다.
        User user = userRepository.findByEmail(request.email())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        String sessionId = UUID.randomUUID().toString();
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), sessionId);
        authSessionRepository.save(AuthSession.builder()
                .id(sessionId)
                .user(user)
                .refreshTokenHash(RefreshTokenHasher.hash(refreshToken))
                .expiresAt(refreshTokenExpiresAt())
                .build());

        String accessToken = jwtProvider.createAccessToken(user.getId(), sessionId);
        return new AuthResult<>(
                LoginResponse.of(accessToken, jwtProvider.getAccessTokenExpirationSeconds(), user), refreshToken);
    }

    /**
     * 리프레시 토큰을 새 토큰으로 교체(회전)한다. 이미 교체된 토큰이 다시 들어오면 탈취로 보고 세션을 폐기한다.
     * 이때 예외를 던지면서도 세션 폐기는 커밋되어야 하므로 CustomException에는 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = CustomException.class)
    public AuthResult<TokenRefreshResponse> refresh(String refreshToken) {
        String sessionId = jwtProvider.getSessionIdFromRefreshToken(refreshToken);

        // 같은 토큰으로 동시에 요청해도 하나만 성공하도록 행을 잠근 뒤 비교·교체한다.
        AuthSession session = authSessionRepository.findByIdForUpdate(sessionId)
                .filter(found -> found.isActive(LocalDateTime.now()))
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_EXPIRED));

        if (!session.hasRefreshTokenHash(RefreshTokenHasher.hash(refreshToken))) {
            session.revoke();
            log.warn("[RefreshTokenReuse] 이미 교체된 리프레시 토큰이 사용되어 세션을 폐기합니다. sessionId={}", sessionId);
            throw new CustomException(ErrorCode.SESSION_EXPIRED);
        }

        Long userId = session.getUser().getId();
        String newRefreshToken = jwtProvider.createRefreshToken(userId, sessionId);
        session.rotate(RefreshTokenHasher.hash(newRefreshToken), refreshTokenExpiresAt());

        String newAccessToken = jwtProvider.createAccessToken(userId, sessionId);
        return new AuthResult<>(
                new TokenRefreshResponse(newAccessToken, jwtProvider.getAccessTokenExpirationSeconds()),
                newRefreshToken);
    }

    /**
     * 액세스 토큰이나 리프레시 쿠키가 가리키는 세션을 폐기한다.
     * 이미 폐기됐거나 토큰이 만료됐어도 로그아웃 결과는 같으므로 에러 없이 끝낸다.
     * 우리가 발급한 토큰이 하나도 없을 때만 401로 응답한다.
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken == null && refreshToken == null) {
            throw new CustomException(ErrorCode.AUTH_REQUIRED);
        }

        List<Claims> verified = Stream.of(
                        jwtProvider.parseIgnoringExpiration(accessToken).filter(jwtProvider::isAccessToken),
                        jwtProvider.parseIgnoringExpiration(refreshToken).filter(jwtProvider::isRefreshToken))
                .flatMap(Optional::stream)
                .toList();
        if (verified.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        verified.stream()
                .map(jwtProvider::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                // 두 세션을 잠글 때 순서를 고정해 교착을 막는다.
                .sorted()
                .forEach(sessionId -> authSessionRepository.findByIdForUpdate(sessionId)
                        .ifPresent(AuthSession::revoke));
    }

    private LocalDateTime refreshTokenExpiresAt() {
        return LocalDateTime.now().plusSeconds(jwtProvider.getRefreshTokenExpirationSeconds());
    }
}
