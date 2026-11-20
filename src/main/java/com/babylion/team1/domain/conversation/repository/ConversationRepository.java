package com.babylion.team1.domain.conversation.repository;

import com.babylion.team1.domain.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
}
