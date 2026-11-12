package com.babylion.team1.domain.policy.repository;

import com.babylion.team1.domain.policy.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
}
