package com.babylion.team1.domain.policy.repository;

import com.babylion.team1.domain.policy.entity.ProjectPolicyMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectPolicyMappingRepository extends JpaRepository<ProjectPolicyMapping, Long> {

	/** 현재 페이지의 버전에 대해서만 같은 회사 프로젝트 수를 일괄 집계한다. */
	@Query("""
			select m.policyVersion.id as versionId, count(distinct m.project.id) as projectCount
			from ProjectPolicyMapping m
			where m.policyVersion.id in :versionIds
			  and m.policyVersion.policy.company.id = :companyId
			  and m.project.company.id = :companyId
			group by m.policyVersion.id
			""")
	List<VersionProjectCount> countProjectsByVersionIds(@Param("versionIds") List<Long> versionIds,
			@Param("companyId") Long companyId);

	interface VersionProjectCount {
		Long getVersionId();
		Long getProjectCount();
	}

	/** 현재 페이지 버전들의 같은 회사 프로젝트만 중복 없이 조회한다. */
	@Query("""
			select distinct m.policyVersion.id as versionId,
			       m.project.id as projectId, m.project.projectName as projectName
			from ProjectPolicyMapping m
			where m.policyVersion.id in :versionIds
			  and m.policyVersion.policy.company.id = :companyId
			  and m.project.company.id = :companyId
			order by m.policyVersion.id, m.project.id
			""")
	List<VersionProjectRow> findProjectsByVersionIds(@Param("versionIds") List<Long> versionIds,
			@Param("companyId") Long companyId);

	interface VersionProjectRow {
		Long getVersionId();
		Long getProjectId();
		String getProjectName();
	}

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
