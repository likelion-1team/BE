package com.babylion.team1.domain.workrequest.repository;

import com.babylion.team1.domain.workrequest.entity.WorkRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkRequestRepository extends JpaRepository<WorkRequest, Long> {
}
