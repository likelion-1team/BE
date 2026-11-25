package com.babylion.team1.domain.deletion.repository;

import com.babylion.team1.domain.deletion.entity.DeletionJob;
import com.babylion.team1.domain.deletion.entity.DeletionJobStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DeletionJobRepository extends JpaRepository<DeletionJob, Long> {

    Optional<DeletionJob> findByIdAndUserId(Long id, Long userId);

    @Query("select j.id from DeletionJob j where j.version.id = :versionId order by j.id desc")
    List<Long> findIdsByVersionId(Long versionId, Limit limit);

    @Query("select j.version.id from DeletionJob j where j.id = :id")
    Optional<Long> findVersionId(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from DeletionJob j where j.id = :id")
    Optional<DeletionJob> findByIdForUpdate(Long id);

    @Query("""
            select j.id from DeletionJob j
            where j.status in :statuses and j.requestedAt < :cutoff
            order by j.requestedAt, j.id
            """)
    List<Long> findStaleIds(Collection<DeletionJobStatus> statuses, Instant cutoff, Limit limit);
}
