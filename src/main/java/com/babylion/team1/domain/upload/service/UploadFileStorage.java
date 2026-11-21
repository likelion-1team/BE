package com.babylion.team1.domain.upload.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
