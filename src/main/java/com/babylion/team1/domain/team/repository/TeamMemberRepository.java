package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.dto.response.MyTeamResponse;
import com.babylion.team1.domain.team.dto.response.TeamMemberListResponse;
import com.babylion.team1.domain.team.dto.response.TeamSubmissionResponse;
import com.babylion.team1.domain.team.entity.TeamMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

	/** 팀 멤버 여부와 역할 확인용 */
	Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

	long countByTeamId(Long teamId);

	@Query("""
		select new com.babylion.team1.domain.team.dto.response.MyTeamResponse(
			team.id, team.name, membership.role, count(rosterMember.id))
		from TeamMember membership
		join membership.team team
		join TeamMember rosterMember on rosterMember.team = team
		where membership.user.id = :userId
		group by team.id, team.name, membership.role, team.createdAt
		order by team.createdAt desc, team.id desc
		""")
	List<MyTeamResponse> findMyTeams(@Param("userId") Long userId);

	@Query("""
		select new com.babylion.team1.domain.team.dto.response.TeamMemberListResponse(
			rosterUser.id, rosterUser.name, rosterUser.email, membership.role,
			case when exists (
				select 1 from Conversation c
				where c.team = membership.team
				  and c.user = rosterUser
				  and c.conversationDate between settings.targetFrom and settings.targetTo
				  and c.upload.status = com.babylion.team1.domain.upload.entity.UploadStatus.DONE
			) then true else false end)
		from TeamMember membership
		join membership.user rosterUser
		left join TeamSettings settings on settings.team = membership.team and settings.current = true
		where membership.team.id = :teamId
		order by membership.joinedAt, membership.id
		""")
	List<TeamMemberListResponse> findTeamMembersWithSubmission(@Param("teamId") Long teamId);

	@Query("""
		select new com.babylion.team1.domain.team.dto.response.TeamSubmissionResponse(
			count(distinct case when completedUpload.status is not null then membership.id else null end),
			count(distinct membership.id),
			count(completedUpload.status),
			settings.targetCount)
		from TeamMember membership
		left join TeamSettings settings on settings.team = membership.team and settings.current = true
		left join Conversation c on c.team = membership.team
			and c.user = membership.user
			and c.conversationDate between settings.targetFrom and settings.targetTo
		left join c.upload completedUpload on completedUpload.status = com.babylion.team1.domain.upload.entity.UploadStatus.DONE
		where membership.team.id = :teamId
		group by settings.targetCount
		""")
	TeamSubmissionResponse findTeamSubmissionSummary(@Param("teamId") Long teamId);
}
