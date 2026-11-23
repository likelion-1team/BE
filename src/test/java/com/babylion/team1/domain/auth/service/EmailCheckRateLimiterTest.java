package com.babylion.team1.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailCheckRateLimiterTest {

    private final AtomicLong now = new AtomicLong();
    private final EmailCheckRateLimiter limiter = new EmailCheckRateLimiter(2, Duration.ofSeconds(60), now::get);

    @Test
    @DisplayName("허용 횟수를 넘으면 RATE_LIMITED 예외를 던진다")
    void rejectsOverLimit() {
        limiter.check("1.1.1.1");
        limiter.check("1.1.1.1");

        assertRateLimited("1.1.1.1");
    }

    @Test
    @DisplayName("IP마다 따로 센다")
    void countsPerIp() {
        limiter.check("1.1.1.1");
        limiter.check("1.1.1.1");

        assertThatCode(() -> limiter.check("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시간 창이 지나면 다시 호출할 수 있다")
    void resetsAfterWindow() {
        limiter.check("1.1.1.1");
        limiter.check("1.1.1.1");
        now.set(Duration.ofSeconds(60).toNanos());

        assertThatCode(() -> limiter.check("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정리 작업은 아직 지나지 않은 시간 창의 횟수를 지우지 않는다")
    void evictionKeepsActiveWindow() {
        limiter.check("1.1.1.1");
        limiter.check("1.1.1.1");
        now.set(Duration.ofSeconds(59).toNanos());
        limiter.evictExpired();

        assertRateLimited("1.1.1.1");
    }

    private void assertRateLimited(String clientIp) {
        assertThatThrownBy(() -> limiter.check(clientIp))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RATE_LIMITED);
    }
}
