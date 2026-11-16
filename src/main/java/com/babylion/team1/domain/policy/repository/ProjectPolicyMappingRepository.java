package com.babylion.team1.domain.policy.repository;

import com.babylion.team1.domain.policy.entity.ProjectPolicyMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectPolicyMappingRepository extends JpaRepository<ProjectPolicyMapping, Long> {

	/**
	 * 대상 프로젝트들에 매핑된 정책 버전을 (버전, 프로젝트) 행 단위로 조회한다.
	 * 같은 회사 정책만 포함하며, 버전별 projectIds 묶기는 서비스에서 처리한다.
	 */
	@Query("""
			select v.id as versionId,
			       v.versionName as versionName,
			       p.id as policyId,
			       p.taskType as taskType,
			       m.project.id as projectId
			from ProjectPolicyMapping m
			join m.policyVersion v
			join v.policy p
			where m.project.id in :projectIds
			  and p.company.id = :companyId
			order by v.id, m.project.id
			""")
	List<PolicyVersionProjectRow> findPolicyVersionRows(@Param("projectIds") List<Long> projectIds,
			@Param("companyId") Long companyId);

	/** findPolicyVersionRows 조회 결과 프로젝션 */
	interface PolicyVersionProjectRow {
		Long getVersionId();

		String getVersionName();

		Long getPolicyId();

		String getTaskType();

		Long getProjectId();
	}
}
