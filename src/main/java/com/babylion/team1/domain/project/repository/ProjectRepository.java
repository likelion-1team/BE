package com.babylion.team1.domain.project.repository;

import com.babylion.team1.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
