package com.babylion.team1.domain.project.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 작업분업구조(WBS) */
@Entity
@Table(name = "wbs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wbs {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "wbs_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(name = "wbs_code", nullable = false, length = 50)
	private String wbsCode;

	@Column(name = "wbs_name", nullable = false, length = 100)
	private String wbsName;

	@Builder
	private Wbs(Project project, String wbsCode, String wbsName) {
		this.project = project;
		this.wbsCode = wbsCode;
		this.wbsName = wbsName;
	}
}
