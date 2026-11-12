package com.babylion.team1.domain.project.entity;

import com.babylion.team1.domain.company.entity.Company;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 프로젝트 */
@Entity
@Table(name = "project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "project_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "company_id", nullable = false)
	private Company company;

	@Column(name = "project_code", nullable = false, length = 50)
	private String projectCode;

	@Column(name = "project_name", nullable = false, length = 100)
	private String projectName;

	@Builder
	private Project(Company company, String projectCode, String projectName) {
		this.company = company;
		this.projectCode = projectCode;
		this.projectName = projectName;
	}
}
