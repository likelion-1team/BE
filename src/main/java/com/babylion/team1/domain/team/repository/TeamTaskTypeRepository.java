package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.entity.TeamTaskType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamTaskTypeRepository extends JpaRepository<TeamTaskType, Long> {

    List<TeamTaskType> findByTeamIdAndIsActiveTrueOrderById(Long teamId);

    Optional<TeamTaskType> findByTeamIdAndName(Long teamId, String name);
}
