package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@EnableScheduling
class UploadFileExpiry {

    private static final Duration RETENTION = Duration.ofHours(24);
    private static final Pattern STORED_NAME = Pattern.compile("([1-9][0-9]{0,18})\\.export");
    private static final Pattern STAGED_NAME = Pattern.compile("upload-[0-9]+\\.part");

    private final UploadFileStorage storage;
    private final UploadRepository uploadRepository;
    private final TransactionTemplate transaction;

    UploadFileExpiry(UploadFileStorage storage, UploadRepository uploadRepository,
                     PlatformTransactionManager transactionManager) {
        this.storage = storage;
        this.uploadRepository = uploadRepository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 60_000)
    void expireUnsubmittedFiles() {
        Path directory = storage.directory();
        if (!Files.isDirectory(directory)) {
            return;
        }
        Instant staleFileBefore = Instant.now().minus(RETENTION);
        LocalDateTime uploadedBefore = LocalDateTime.now().minus(RETENTION);
        try (var files = Files.list(directory)) {
            files.forEach(path -> expireFile(path, staleFileBefore, uploadedBefore));
        } catch (IOException e) {
            log.warn("임시 업로드 디렉터리를 읽을 수 없습니다.", e);
        }
    }

    private void expireFile(Path path, Instant staleFileBefore, LocalDateTime uploadedBefore) {
        String name = path.getFileName().toString();
        try {
            if (!Files.isRegularFile(path) || Files.getLastModifiedTime(path).toInstant().isAfter(staleFileBefore)) {
                return;
            }
            if (STAGED_NAME.matcher(name).matches()) {
                storage.delete(path);
                return;
            }
            var match = STORED_NAME.matcher(name);
            if (match.matches()) {
                Long uploadId = Long.valueOf(match.group(1));
                transaction.executeWithoutResult(status -> {
                    // 분석 시작도 같은 행 잠금으로 상태를 확인한 뒤 파일을 사용한다.
                    boolean expired = uploadRepository.findByIdForUpdate(uploadId)
                            .map(upload -> upload.getStatus() == UploadStatus.UPLOADED
                                    && !upload.getUploadedAt().isAfter(uploadedBefore))
                            .orElse(true);
                    if (expired) {
                        storage.delete(path);
                    }
                });
            }
        } catch (IOException | RuntimeException e) {
            log.warn("임시 업로드 만료 처리 실패: {}", name, e);
        }
    }
}
