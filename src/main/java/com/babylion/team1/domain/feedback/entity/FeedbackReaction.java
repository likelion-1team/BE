package com.babylion.team1.domain.feedback.entity;

import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/** 피드백 반응 */
@Entity
@Table(
	name = "feedback_reactions",
	uniqueConstraints = @UniqueConstraint(name = "uk_feedback_reactions_feedback_user", columnNames = {"feedback_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedbackReaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "feedback_id", nullable = false)
	private Feedback feedback;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "reaction", nullable = false, length = 20)
	private ReactionType reaction;

	/** 해당 없음 사유(선택) */
	@Column(name = "reason", length = 500)
	private String reason;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Builder
	private FeedbackReaction(Feedback feedback, User user, ReactionType reaction, String reason) {
		this.feedback = feedback;
		this.user = user;
		this.reaction = reaction;
		this.reason = reason;
	}

	public void update(ReactionType reaction, String reason) {
		this.reaction = reaction;
		this.reason = reason;
	}
}
