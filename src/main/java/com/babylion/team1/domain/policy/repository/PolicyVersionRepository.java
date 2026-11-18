package com.babylion.team1.domain.policy.repository;

import com.babylion.team1.domain.policy.entity.PolicyVersion;
import com.babylion.team1.domain.policy.entity.PolicyVersionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

	/** 작성자는 단일 연관으로 조회하고, 프로젝트 매핑은 페이지 조회 후 일괄 조회한다. */
	@Query(value = """
			select v.id as versionId, v.versionName as versionName, v.status as status,
			       u.id as createdById, u.userName as createdByName,
			       v.createdAt as createdAt, v.appliedAt as appliedAt
			from PolicyVersion v join v.policy p join v.createdBy u
			where p.id = :policyId and p.company.id = :companyId
			order by v.createdAt desc, v.id desc
			""", countQuery = """
			select count(v) from PolicyVersion v join v.policy p
			where p.id = :policyId and p.company.id = :companyId
			""")
	Page<PolicyVersionHistoryRow> findVersionHistory(@Param("policyId") Long policyId,
			@Param("companyId") Long companyId, Pageable pageable);

	@Query("""
			select v from PolicyVersion v
			join fetch v.policy p
			join fetch v.createdBy
			left join fetch v.approvedBy
			where v.id = :versionId and p.id = :policyId and p.company.id = :companyId
			""")
	Optional<PolicyVersion> findVersionDetail(@Param("policyId") Long policyId,
			@Param("versionId") Long versionId, @Param("companyId") Long companyId);

	interface PolicyVersionHistoryRow {
		Long getVersionId();
		String getVersionName();
		PolicyVersionStatus getStatus();
		Long getCreatedById();
		String getCreatedByName();
		LocalDateTime getCreatedAt();
		LocalDateTime getAppliedAt();
	}

	interface PolicyVersionListRow {
		Long getPolicyId();
		String getTaskType();
		Long getVersionId();
		String getVersionName();
		PolicyVersionStatus getStatus();
		LocalDateTime getAppliedAt();
	}
}
