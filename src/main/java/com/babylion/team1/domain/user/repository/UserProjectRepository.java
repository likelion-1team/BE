package com.babylion.team1.domain.user.repository;

import com.babylion.team1.domain.user.entity.UserProject;
import com.babylion.team1.domain.user.entity.UserProjectId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProjectRepository extends JpaRepository<UserProject, UserProjectId> {
}
