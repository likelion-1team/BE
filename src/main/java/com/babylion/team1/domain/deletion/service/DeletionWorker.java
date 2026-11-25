package com.babylion.team1.domain.deletion.service;

import com.babylion.team1.domain.deletion.entity.DeletionJobStatus;
import com.babylion.team1.domain.deletion.repository.DeletionJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
class DeletionWorker {

    private static final Duration STALE_AFTER = Duration.ofMinutes(10);
    private static final int RECOVERY_BATCH = 100;

    private final DeletionJobRepository jobs;
    private final DeletionProcessor processor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void run(DeletionRequestedEvent event) {
        processor.process(event.jobId());
    }

    /** 서버 재시작으로 멈췄거나 일시 오류로 실패한 작업을 다시 처리한다(v4 명세 8장: 일시 실패는 내부 복구). */
    @Scheduled(fixedDelay = 60_000)
    public void recover() {
        jobs.findStaleIds(
                List.of(DeletionJobStatus.PENDING, DeletionJobStatus.RUNNING, DeletionJobStatus.FAILED),
                Instant.now().minus(STALE_AFTER), Limit.of(RECOVERY_BATCH)).forEach(this::reprocess);
    }

    private void reprocess(Long jobId) {
        try {
            processor.process(jobId);
        } catch (RuntimeException exception) {
            // 한 작업의 상태 저장 실패가 나머지 작업을 막지 않게 한다.
            log.warn("삭제 작업 {} 재처리 실패 ({})", jobId, exception.getClass().getSimpleName());
        }
    }
}
