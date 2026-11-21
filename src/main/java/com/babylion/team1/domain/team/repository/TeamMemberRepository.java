package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.entity.TeamMember;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

	/** 팀 멤버 여부와 역할 확인용 */
	Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);

	long countByTeamId(Long teamId);
}
