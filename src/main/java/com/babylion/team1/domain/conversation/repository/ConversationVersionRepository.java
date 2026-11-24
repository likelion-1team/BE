package com.babylion.team1.domain.conversation.repository;

import com.babylion.team1.domain.conversation.entity.ConversationVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationVersionRepository extends JpaRepository<ConversationVersion, Long> {
    Optional<ConversationVersion> findFirstByConversationIdOrderByInputVersionDesc(Long conversationId);
    boolean existsByConversationIdAndContentHash(Long conversationId, String contentHash);
}
