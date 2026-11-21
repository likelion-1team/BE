package com.babylion.team1.domain.upload.repository;

import com.babylion.team1.domain.upload.entity.Upload;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadRepository extends JpaRepository<Upload, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Upload u where u.id = :id")
    Optional<Upload> findByIdForUpdate(@Param("id") Long id);
}
