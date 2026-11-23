package com.babylion.team1.domain.auth.repository;

import com.babylion.team1.domain.auth.entity.AuthSession;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

	boolean existsByIdAndUserIdAndRevokedFalseAndExpiresAtAfter(String id, Long userId, LocalDateTime now);

	/** 재발급·폐기가 동시에 들어와도 한 번에 하나만 처리되도록 트랜잭션이 끝날 때까지 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from AuthSession s where s.id = :id")
	Optional<AuthSession> findByIdForUpdate(@Param("id") String id);
}
