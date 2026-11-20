package com.babylion.team1.domain.feedback.entity;

import com.babylion.team1.domain.conversation.entity.Conversation;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 피드백 근거 대화 */
@Entity
@Table(name = "feedback_evidences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedbackEvidence {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "feedback_id", nullable = false)
	private Feedback feedback;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "conversation_id", nullable = false)
	private Conversation conversation;

	/** 문제 턴(messages.idx) 목록. 없으면 [] */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "turn_indexes", nullable = false)
	private List<Integer> turnIndexes = new ArrayList<>();

	@Builder
	private FeedbackEvidence(Feedback feedback, Conversation conversation, List<Integer> turnIndexes) {
		this.feedback = feedback;
		this.conversation = conversation;
		if (turnIndexes != null) {
			this.turnIndexes = new ArrayList<>(turnIndexes);
		}
	}
}
