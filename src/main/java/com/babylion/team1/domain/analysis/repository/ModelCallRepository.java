package com.babylion.team1.domain.analysis.repository;

import com.babylion.team1.domain.analysis.entity.ModelCall;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelCallRepository extends JpaRepository<ModelCall, Long> {
    int countByItemIdAndPurpose(Long itemId, ModelCall.Purpose purpose);
}
