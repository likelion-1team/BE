package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.entity.TeamSettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamSettingsRepository extends JpaRepository<TeamSettings, Long> {

	Optional<TeamSettings> findByTeamId(Long teamId);
}
