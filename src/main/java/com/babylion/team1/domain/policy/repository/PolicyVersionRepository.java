package com.babylion.team1.domain.policy.repository;

import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {

	/** 매핑 조인 없이 버전 단위로 페이지를 조회하여 중복 행과 잘못된 count를 방지한다. */
	@Query(value = """
			select p.id as policyId, p.taskType as taskType,
			       v.id as versionId, v.versionName as versionName,
			       v.status as status, v.appliedAt as appliedAt
			from PolicyVersion v join v.policy p
			where p.company.id = :companyId
			  and (:taskType is null or p.taskType = :taskType)
			  and v.status in :statuses
			order by v.createdAt desc, v.id desc
			""", countQuery = """
			select count(v) from PolicyVersion v join v.policy p
			where p.company.id = :companyId
			  and (:taskType is null or p.taskType = :taskType)
			  and v.status in :statuses
			""")
	Page<PolicyVersionListRow> findPolicyList(@Param("companyId") Long companyId,
			@Param("taskType") String taskType, @Param("statuses") List<PolicyVersionStatus> statuses,
			Pageable pageable);

	interface PolicyVersionListRow {
		Long getPolicyId();
		String getTaskType();
		Long getVersionId();
		String getVersionName();
		PolicyVersionStatus getStatus();
		LocalDateTime getAppliedAt();
	}
}
