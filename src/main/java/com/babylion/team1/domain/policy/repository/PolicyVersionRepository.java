package com.babylion.team1.domain.policy.repository;

import com.babylion.team1.domain.policy.entity.PolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {
}
