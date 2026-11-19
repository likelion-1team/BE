package com.babylion.team1.domain.policy.repository;

import com.babylion.team1.domain.policy.entity.Policy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
	boolean existsByIdAndCompany_Id(Long id, Long companyId);
	Optional<Policy> findByIdAndCompany_Id(Long id, Long companyId);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Policy p where p.id = :id and p.company.id = :companyId")
	Optional<Policy> lockForApply(@Param("id") Long id, @Param("companyId") Long companyId);
}
