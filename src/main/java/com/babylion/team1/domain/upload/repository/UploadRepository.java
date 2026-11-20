package com.babylion.team1.domain.upload.repository;

import com.babylion.team1.domain.upload.entity.Upload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRepository extends JpaRepository<Upload, Long> {
}
