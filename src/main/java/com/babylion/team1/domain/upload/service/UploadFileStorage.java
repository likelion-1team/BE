package com.babylion.team1.domain.upload.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class UploadFileStorage {

    private final Path directory;

    UploadFileStorage(@Value("${app.upload.directory}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    Path stage(MultipartFile file) {
        Path staged = null;
        try {
            Files.createDirectories(directory);
            staged = Files.createTempFile(directory, "upload-", ".part");
            try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(staged)) {
                input.transferTo(output);
            }
            return staged;
        } catch (IOException e) {
            if (staged != null) {
                delete(staged);
            }
            throw new UncheckedIOException("임시 업로드 파일을 저장할 수 없습니다.", e);
        }
    }

    void store(Path staged, Long uploadId) {
        try {
            Files.move(staged, pathFor(uploadId), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 원본을 연결할 수 없습니다.", e);
        }
    }

    public Path pathFor(Long uploadId) {
        return directory.resolve(uploadId + ".export");
    }

    void writeTargetPeriod(Long uploadId, TargetPeriod period) {
        try {
            Files.write(periodPath(uploadId), List.of(period.from().toString(), period.to().toString()));
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 분석 대상 기간을 보관할 수 없습니다.", e);
        }
    }

    Optional<TargetPeriod> readTargetPeriod(Long uploadId) {
        try {
            var lines = Files.readAllLines(periodPath(uploadId));
            return Optional.of(new TargetPeriod(LocalDate.parse(lines.get(0)), LocalDate.parse(lines.get(1))));
        } catch (java.nio.file.NoSuchFileException e) {
            // 업로드 검증 API에서 기간을 기록하기 전에 생성된 파일이다.
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 분석 대상 기간을 읽을 수 없습니다.", e);
        }
    }

    void markFailed(Long uploadId) {
        try {
            Files.setLastModifiedTime(pathFor(uploadId), FileTime.from(Instant.now()));
        } catch (IOException e) {
            log.warn("실패한 업로드의 파일 보관 시점을 갱신할 수 없습니다: {}", uploadId);
        }
    }

    void deleteUpload(Long uploadId) {
        delete(pathFor(uploadId));
        if (Files.notExists(pathFor(uploadId))) {
            delete(periodPath(uploadId));
        }
    }

    private Path periodPath(Long uploadId) {
        return directory.resolve(uploadId + ".period");
    }

    record TargetPeriod(LocalDate from, LocalDate to) {
    }

    Path directory() {
        return directory;
    }

    void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("임시 업로드 파일 정리 실패: {}", file.getFileName(), e);
        }
    }
}
