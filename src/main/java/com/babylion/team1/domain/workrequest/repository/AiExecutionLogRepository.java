package com.babylion.team1.domain.workrequest.repository;

import com.babylion.team1.domain.workrequest.entity.AiExecutionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiExecutionLogRepository extends JpaRepository<AiExecutionLog, Long> {

	/** 대상 프로젝트들에서 발생한 AI 실행 로그의 모델 버전 Distinct 목록 (null 제외) */
	@Query("""
			select distinct l.modelVersion from AiExecutionLog l
			join l.workRequest w
			where w.project.id in :projectIds
			  and l.modelVersion is not null
			order by l.modelVersion
			""")
	List<String> findDistinctModelVersions(@Param("projectIds") List<Long> projectIds);
}
