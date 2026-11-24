package com.babylion.team1.domain.conversation.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/** 대화 안의 메시지 */
@Entity
@Table(
	name = "messages",
	indexes = @Index(name = "idx_messages_conversation_id", columnList = "conversation_id"),
	uniqueConstraints = @UniqueConstraint(name = "uk_messages_version_idx", columnNames = {"version_id", "idx"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "conversation_id", nullable = false)
	private Conversation conversation;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "version_id")
	private ConversationVersion version;

	/** 대화 안 순서(0부터) */
	@Column(name = "idx", nullable = false)
	private Integer idx;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "role", nullable = false, length = 20)
	private MessageRole role;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	/** 수정·재요청 턴 여부 */
	@ColumnDefault("false")
	@Column(name = "is_rework", nullable = false)
	private Boolean isRework = false;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Builder
	private Message(Conversation conversation, ConversationVersion version, Integer idx, MessageRole role, String content, Boolean isRework,
		LocalDateTime createdAt) {
		this.conversation = conversation;
		this.version = version;
		this.idx = idx;
		this.role = role;
		this.content = content;
		if (isRework != null) {
			this.isRework = isRework;
		}
		this.createdAt = createdAt;
	}
	public void markRework(boolean rework) {
		this.isRework = rework;
	}
}
