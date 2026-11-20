package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
