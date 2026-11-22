package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.dto.response.MyTeamResponse;
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
}
