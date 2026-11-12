package com.babylion.team1.domain.budget.entity;

import com.babylion.team1.domain.project.entity.Project;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 예산 차수 */
@Entity
@Table(name = "budget_revision")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BudgetRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "revision_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(name = "revision_number", nullable = false)
	private Integer revisionNumber;

	@Column(name = "is_confirmed", nullable = false)
	private Boolean isConfirmed;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	private BudgetRevision(Project project, Integer revisionNumber, Boolean isConfirmed) {
		this.project = project;
		this.revisionNumber = revisionNumber;
		this.isConfirmed = isConfirmed;
	}
}
