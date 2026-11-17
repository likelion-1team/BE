package com.babylion.team1.domain.auth.repository;

import com.babylion.team1.domain.auth.entity.RefreshSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshSession> findByTokenHash(String tokenHash);
}
