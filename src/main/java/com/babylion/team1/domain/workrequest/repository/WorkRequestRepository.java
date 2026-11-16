package com.babylion.team1.domain.workrequest.repository;

import com.babylion.team1.domain.workrequest.entity.WorkRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkRequestRepository extends JpaRepository<WorkRequest, Long> {

	/** 대상 프로젝트들의 업무 요청 중 가장 이른 생성일시 (없으면 null) */
	@Query("select min(w.createdAt) from WorkRequest w where w.project.id in :projectIds")
	LocalDateTime findMinCreatedAt(@Param("projectIds") List<Long> projectIds);

	/** 대상 프로젝트들의 업무 요청 중 가장 늦은 생성일시 (없으면 null) */
	@Query("select max(w.createdAt) from WorkRequest w where w.project.id in :projectIds")
	LocalDateTime findMaxCreatedAt(@Param("projectIds") List<Long> projectIds);

	/** 대상 프로젝트들에서 발생한 업무 유형(task_type) Distinct 목록 */
	@Query("""
			select distinct w.taskType from WorkRequest w
			where w.project.id in :projectIds
			order by w.taskType
			""")
	List<String> findDistinctTaskTypes(@Param("projectIds") List<Long> projectIds);
}
