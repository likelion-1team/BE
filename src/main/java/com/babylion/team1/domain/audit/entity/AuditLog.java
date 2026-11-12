package com.babylion.team1.domain.audit.entity;

import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 서버 측 감사 로그 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "audit_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 30)
	private AuditEventType eventType;

	@Column(name = "detail", columnDefinition = "json")
	private String detail;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	private AuditLog(User user, AuditEventType eventType, String detail) {
		this.user = user;
		this.eventType = eventType;
		this.detail = detail;
	}
}
