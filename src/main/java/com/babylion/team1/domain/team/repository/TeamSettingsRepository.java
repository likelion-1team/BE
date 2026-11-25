package com.babylion.team1.domain.team.repository;

import com.babylion.team1.domain.team.entity.TeamSettings;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamSettingsRepository extends JpaRepository<TeamSettings, Long> {

	@Query("select s from TeamSettings s where s.team.id = :teamId and s.current = true")
	Optional<TeamSettings> findByTeamId(@Param("teamId") Long teamId);

	/** 팀장이 저장한 기준 버전(버전 오름차순). 팀 생성 시 만든 version 0은 분석에 쓸 수 없어 제외한다 */
	@Query("select s from TeamSettings s where s.team.id = :teamId and s.version > 0 order by s.version")
	List<TeamSettings> findSavedVersions(@Param("teamId") Long teamId);
}
