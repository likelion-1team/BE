package com.babylion.team1.domain.feedback.repository;

import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Content;
import com.babylion.team1.domain.feedback.entity.SharedCase;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SharedCaseRepository extends JpaRepository<SharedCase, Long> {
    Optional<SharedCase> findByFeedbackIdAndStatus(Long feedbackId, SharedCase.Status status);

    @Query("""
            select sharedCase.feedback.id from SharedCase sharedCase
            where sharedCase.id = :sharedCaseId and sharedCase.team.id = :teamId
            """)
    Optional<Long> findFeedbackId(Long sharedCaseId, Long teamId);

    Optional<SharedCase> findByIdAndTeamId(Long sharedCaseId, Long teamId);

    String PUBLIC_CASES = """
            from SharedCase sharedCase
            join sharedCase.feedback feedback
            left join Guide guide on guide.sharedCase = sharedCase
            where sharedCase.team.id = :teamId and sharedCase.status = ACTIVE
              and (:guidesOnly = false or guide.id is not null)
              and (:type is null or cast(function('json_unquote',
                  function('json_extract', sharedCase.content, '$.type')) as String) in (:type, :alternateType))
              and not exists (
                  select 1 from FeedbackEvidence e left join e.conversation.currentVersion currentVersion
                  where e.feedback = feedback and
            """ + FeedbackEvidenceRepository.DELETED_ORIGINAL + ")\n";

    @Query(value = """
            select sharedCase.id as sharedCaseId, feedback.shareVersion as shareVersion,
                sharedCase.taskType.id as taskTypeId, sharedCase.content as content,
                case when guide.id is not null then true else false end as isGuide
            """ + PUBLIC_CASES + """
            order by case when :guidesOnly = true then guide.designatedAt else sharedCase.sharedAt end desc,
                sharedCase.id desc
            """, countQuery = "select count(sharedCase.id) " + PUBLIC_CASES)
    Page<PublicCaseRow> findPublicCases(Long teamId, String type, String alternateType, boolean guidesOnly,
                                       Pageable pageable);

    interface PublicCaseRow {
        Long getSharedCaseId();
        int getShareVersion();
        Long getTaskTypeId();
        Content getContent();
        boolean getIsGuide();
    }
}
