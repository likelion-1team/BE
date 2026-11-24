package com.babylion.team1.domain.conversation.repository;

import com.babylion.team1.domain.conversation.entity.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @org.springframework.data.jpa.repository.Query("select m from Message m where m.conversation.id = :conversationId and m.version is null")
    List<Message> findByConversationId(Long conversationId);

    @org.springframework.data.jpa.repository.Query("""
            select m from Message m where m.conversation.id = :conversationId
            and ((m.conversation.currentVersion is null and m.version is null)
                or m.version = m.conversation.currentVersion)
            order by m.idx asc
            """)
    List<Message> findByConversationIdOrderByIdxAsc(Long conversationId);
}
