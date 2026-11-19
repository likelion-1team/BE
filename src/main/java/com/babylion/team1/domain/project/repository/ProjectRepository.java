package com.babylion.team1.domain.project.repository;

import com.babylion.team1.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
	List<Project> findByIdInAndCompany_Id(Collection<Long> ids, Long companyId);
}
