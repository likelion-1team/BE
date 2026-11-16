package com.babylion.team1.domain.user.repository;

import com.babylion.team1.domain.project.entity.Project;
import com.babylion.team1.domain.user.entity.UserProject;
import com.babylion.team1.domain.user.entity.UserProjectId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProjectRepository extends JpaRepository<UserProject, UserProjectId> {

	/** 사용자가 User_Project 매핑으로 접근 권한을 가진 프로젝트 목록 (같은 회사 프로젝트만) */
	@Query("""
			select up.project from UserProject up
			where up.user.id = :userId
			  and up.project.company.id = :companyId
			order by up.project.id
			""")
	List<Project> findAccessibleProjects(@Param("userId") Long userId, @Param("companyId") Long companyId);
}
