package com.babylion.team1.domain.conversation.repository;

import com.babylion.team1.domain.conversation.entity.ConversationVersion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ConversationVersionRepository extends JpaRepository<ConversationVersion, Long> {
    Optional<ConversationVersion> findFirstByConversationIdOrderByInputVersionDesc(Long conversationId);
    Optional<ConversationVersion> findByConversationIdAndContentHash(Long conversationId, String contentHash);

    boolean existsByIdAndConversationUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ConversationVersion v where v.id = :id")
    Optional<ConversationVersion> findByIdForUpdate(Long id);
}
