package com.babylion.team1.domain.conversation.repository;

import com.babylion.team1.domain.conversation.entity.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByVersionIdOrderByIdxAsc(Long versionId);

    @Query("select m from Message m where m.conversation.id = :conversationId and m.version is null")
    List<Message> findByConversationId(Long conversationId);

    @Query("""
            select m from Message m where m.conversation.id = :conversationId and m.version is null
            order by m.idx asc
            """)
    List<Message> findLegacyMessagesOrdered(Long conversationId);
}
