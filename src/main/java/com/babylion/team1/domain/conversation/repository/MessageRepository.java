package com.babylion.team1.domain.conversation.repository;

import com.babylion.team1.domain.conversation.entity.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationId(Long conversationId);

    List<Message> findByConversationIdOrderByIdxAsc(Long conversationId);
}
