package com.babylion.team1.domain.conversation.repository;

import com.babylion.team1.domain.conversation.entity.Conversation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    java.util.Optional<Conversation> findByTeamIdAndUserIdAndExternalId(Long teamId, Long userId, String externalId);

    List<Conversation> findByUploadId(Long uploadId);

    long countByUploadId(Long uploadId);

    @Query("""
            select c.upload.id as uploadId, count(c) as analyzedCount
            from Conversation c
            where c.upload.id in :uploadIds
            group by c.upload.id
            """)
    List<UploadConversationCount> countByUploadIds(@Param("uploadIds") Collection<Long> uploadIds);

    @Query("""
            select c.externalId as externalId, v.contentHash as contentHash
            from Conversation c join c.currentVersion v
            where c.team.id = :teamId and c.user.id = :userId and c.externalId in :externalIds
            and v.deletedAt is null
            """)
    List<CurrentContentHash> findCurrentContentHashes(Long teamId, Long userId, Collection<String> externalIds);

    interface CurrentContentHash {
        String getExternalId();
        String getContentHash();
    }

    interface UploadConversationCount {
        Long getUploadId();

        long getAnalyzedCount();
    }

    @Query("select c.externalId from Conversation c where c.team.id = :teamId and c.externalId in :externalIds")
    List<String> findExistingExternalIds(@Param("teamId") Long teamId,
                                         @Param("externalIds") Collection<String> externalIds);
}
