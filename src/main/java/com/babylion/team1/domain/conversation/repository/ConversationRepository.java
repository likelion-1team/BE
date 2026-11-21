package com.babylion.team1.domain.conversation.repository;

import com.babylion.team1.domain.conversation.entity.Conversation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("select c.externalId from Conversation c where c.team.id = :teamId and c.externalId in :externalIds")
    List<String> findExistingExternalIds(@Param("teamId") Long teamId,
                                         @Param("externalIds") Collection<String> externalIds);
}
