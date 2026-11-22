package com.babylion.team1.domain.conversation.repository;

import com.babylion.team1.domain.conversation.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    java.util.List<Message> findByConversationId(Long conversationId);
}
