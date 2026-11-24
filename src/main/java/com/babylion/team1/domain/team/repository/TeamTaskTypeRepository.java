package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.entity.TeamTaskType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamTaskTypeRepository extends JpaRepository<TeamTaskType, Long> {

    List<TeamTaskType> findByTeamIdAndIsActiveTrueOrderById(Long teamId);
}
