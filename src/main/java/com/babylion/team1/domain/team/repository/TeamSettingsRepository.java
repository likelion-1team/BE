package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.entity.TeamSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamSettingsRepository extends JpaRepository<TeamSettings, Long> {

	@Query("select s from TeamSettings s where s.team.id = :teamId and s.current = true")
	Optional<TeamSettings> findByTeamId(@Param("teamId") Long teamId);
}
