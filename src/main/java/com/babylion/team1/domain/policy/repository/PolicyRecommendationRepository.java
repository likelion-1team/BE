package com.babylion.team1.domain.policy.repository;

import com.babylion.team1.domain.policy.entity.DecisionResult;
import com.babylion.team1.domain.policy.entity.PolicyRecommendation;
import com.babylion.team1.domain.policy.entity.RecommendationStatus;
import com.babylion.team1.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRecommendationRepository extends JpaRepository<PolicyRecommendation, Long> {

	@Query(value = """
			select r.id as recommendationId, p.id as policyId, p.taskType as taskType,
			       r.status as status, r.observedProblem as observedProblem,
			       r.sampleCount as sampleCount, r.createdAt as createdAt
			from PolicyRecommendation r join r.policy p
			where p.company.id = :companyId
			  and (:policyId is null or p.id = :policyId)
			  and (:status is null or r.status = :status)
			order by r.createdAt desc, r.id desc
			""", countQuery = """
			select count(r) from PolicyRecommendation r join r.policy p
			where p.company.id = :companyId
			  and (:policyId is null or p.id = :policyId)
			  and (:status is null or r.status = :status)
			""")
	Page<RecommendationListRow> findRecommendationList(@Param("companyId") Long companyId,
			@Param("policyId") Long policyId, @Param("status") RecommendationStatus status, Pageable pageable);

	@Query("""
			select r from PolicyRecommendation r
			join fetch r.policy p
			left join fetch r.decidedBy
			where r.id = :id and p.company.id = :companyId
			""")
	Optional<PolicyRecommendation> findRecommendationDetail(@Param("id") Long id, @Param("companyId") Long companyId);

	interface RecommendationListRow {
		Long getRecommendationId();
		Long getPolicyId();
		String getTaskType();
		RecommendationStatus getStatus();
		String getObservedProblem();
		Integer getSampleCount();
		LocalDateTime getCreatedAt();
	}

	/** 회사 소속 검증을 위해 정책·회사까지 함께 조회한다. */
	@Query("""
			select r from PolicyRecommendation r
			join fetch r.policy p
			join fetch p.company
			where r.id = :id
			""")
	Optional<PolicyRecommendation> findWithPolicyById(@Param("id") Long id);

	/**
	 * 아직 결정되지 않은 추천에만 결정을 기록한다. (decided_at IS NULL 조건으로 원자적 동시성 제어)
	 * 반환값이 0이면 이미 처리된 추천이다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update PolicyRecommendation r
			set r.status = :status,
			    r.decisionResult = :decisionResult,
			    r.rejectionReason = :rejectionReason,
			    r.decidedBy = :decidedBy,
			    r.decidedAt = :decidedAt
			where r.id = :id
			  and r.decidedAt is null
			""")
	int recordDecision(@Param("id") Long id,
			@Param("status") RecommendationStatus status,
			@Param("decisionResult") DecisionResult decisionResult,
			@Param("rejectionReason") String rejectionReason,
			@Param("decidedBy") User decidedBy,
			@Param("decidedAt") LocalDateTime decidedAt);
}
