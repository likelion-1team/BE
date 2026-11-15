package com.babylion.team1.domain.user.entity;

import com.babylion.team1.domain.company.entity.Company;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "company_id", nullable = false)
	private Company company;

	@Column(name = "employee_number", nullable = false, length = 50)
	private String employeeNumber;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "user_name", nullable = false, length = 50)
	private String userName;

	@Enumerated(EnumType.STRING)
	@Column(name = "role_type", nullable = false, length = 20)
	private RoleType roleType;

	@Column(name = "failed_login_count", nullable = false)
	private Integer failedLoginCount;

	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;

	@Builder
	private User(Company company, String employeeNumber, String passwordHash, String userName, RoleType roleType, Integer failedLoginCount, LocalDateTime lastLoginAt) {
		this.company = company;
		this.employeeNumber = employeeNumber;
		this.passwordHash = passwordHash;
		this.userName = userName;
		this.roleType = roleType;
		this.failedLoginCount = failedLoginCount;
		this.lastLoginAt = lastLoginAt;
	}
}
