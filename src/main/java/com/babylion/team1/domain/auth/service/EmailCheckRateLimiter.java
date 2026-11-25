package com.babylion.team1.domain.auth.service;

import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 이메일 중복 확인 호출 횟수를 IP별 고정 시간 창으로 제한한다(가입 여부 대량 조회 방지).
 * 메모리에만 저장하므로 서버를 여러 대로 늘리면 서버마다 따로 센다.
 */
@Component
public class EmailCheckRateLimiter {

    private final int maxRequests;
    private final long windowNanos;
    private final LongSupplier nanoClock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public EmailCheckRateLimiter(
            @Value("${app.rate-limit.email-check.max-requests}") int maxRequests,
            @Value("${app.rate-limit.email-check.window}") Duration window) {
        this(maxRequests, window, System::nanoTime);
    }

    EmailCheckRateLimiter(int maxRequests, Duration window, LongSupplier nanoClock) {
        this.maxRequests = maxRequests;
        this.windowNanos = window.toNanos();
        this.nanoClock = nanoClock;
    }

    /** 허용 횟수를 넘으면 RATE_LIMITED 예외를 던진다. */
    public void check(String clientIp) {
        long now = nanoClock.getAsLong();
        Window window = windows.compute(clientIp, (ip, current) ->
                current == null || isOver(current, now)
                        ? new Window(now, 1)
                        : new Window(current.startedAt(), current.count() + 1));
        if (window.count() > maxRequests) {
            throw new CustomException(ErrorCode.RATE_LIMITED);
        }
    }

    /** 다시 호출하지 않는 IP가 메모리에 계속 쌓이지 않도록 지난 창을 지운다. */
    @Scheduled(fixedDelay = 60_000)
    void evictExpired() {
        long now = nanoClock.getAsLong();
        windows.values().removeIf(window -> isOver(window, now));
    }

    private boolean isOver(Window window, long now) {
        return now - window.startedAt() >= windowNanos;
    }

    private record Window(long startedAt, int count) {
    }
}
