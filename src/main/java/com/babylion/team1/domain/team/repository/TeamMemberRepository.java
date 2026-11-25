package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.dto.response.MyTeamResponse;
import com.babylion.team1.domain.team.dto.response.TeamMemberListResponse;
import com.babylion.team1.domain.team.entity.TeamMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

	/** 팀 멤버 여부와 역할 확인용 */
	Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

	long countByTeamId(Long teamId);

	@Query(value = """
		select new com.babylion.team1.domain.team.dto.response.MyTeamResponse(
			team.id, team.name, team.description, membership.role, count(rosterMember.id))
		from TeamMember membership
		join membership.team team
		join TeamMember rosterMember on rosterMember.team = team
		where membership.user.id = :userId
		group by team.id, team.name, team.description, membership.role, team.createdAt
		order by team.createdAt desc, team.id desc
		""", countQuery = """
		select count(membership.id) from TeamMember membership where membership.user.id = :userId
		""")
	Page<MyTeamResponse> findMyTeams(@Param("userId") Long userId, Pageable pageable);

	String V4_SELECTION = """
			item.team = membership.team
			and item.user = membership.user
			and item.conversationDate between settings.targetFrom and settings.targetTo
			and exists (select 1 from Analysis a where a = item.analysis and a.settings = settings)
			and exists (select 1 from ConversationVersion v where v = item.version and v.deletedAt is null)
			""";

	String LEGACY_SUBMISSION = """
			c.team = membership.team
			and c.user = membership.user
			and c.conversationDate between settings.targetFrom and settings.targetTo
			and c.currentVersion is null
			and exists (select 1 from Upload u where u = c.upload and u.status = DONE)
			""";

	@Query("""
		select new com.babylion.team1.domain.team.dto.response.TeamMemberListResponse(
			rosterUser.id, rosterUser.name, rosterUser.email, membership.role,
			case when exists (select 1 from AnalysisItem item where
			""" + V4_SELECTION + """
			) or exists (select 1 from Conversation c where
			""" + LEGACY_SUBMISSION + """
			) then true else false end)
		from TeamMember membership
		join membership.user rosterUser
		left join TeamSettings settings on settings.team = membership.team and settings.current = true
		where membership.team.id = :teamId
		order by membership.joinedAt, membership.id
		""")
	List<TeamMemberListResponse> findTeamMembersWithSubmission(@Param("teamId") Long teamId);

	@Query("""
		select new com.babylion.team1.domain.team.repository.TeamMemberRepository$SubmissionSummary(
			count(distinct case when item.id is not null or c.id is not null then membership.id else null end),
			count(distinct membership.id),
			count(distinct case when (item.status = SUCCEEDED and item.current = true) or c.id is not null
				then membership.id else null end),
			count(distinct case when item.status = SUCCEEDED and item.current = true then item.id else null end)
				+ count(distinct c.id),
			settings.targetCount,
			coalesce(settings.version, 0))
		from TeamMember membership
		left join TeamSettings settings on settings.team = membership.team and settings.current = true
		left join AnalysisItem item on
		""" + V4_SELECTION + """
		left join Conversation c on
		""" + LEGACY_SUBMISSION + """
		where membership.team.id = :teamId
		group by settings.targetCount, settings.version
		""")
	SubmissionSummary findTeamSubmissionSummary(@Param("teamId") Long teamId);

	record SubmissionSummary(long submittedMembers, long totalMembers, long contributors,
							 long analyzedConversations, Integer targetCount, Integer settingsVersion) {}
}
