package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
}
