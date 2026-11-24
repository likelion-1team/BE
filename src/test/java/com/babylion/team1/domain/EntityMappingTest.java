package com.babylion.team1.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babylion.team1.domain.analysis.entity.Analysis;
import com.babylion.team1.domain.analysis.entity.AnalysisItem;
import com.babylion.team1.domain.analysis.entity.AnalysisItemStatus;
import com.babylion.team1.domain.analysis.entity.AnalysisStatus;
import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.conversation.entity.ConversationVersion;
import com.babylion.team1.domain.conversation.entity.Message;
import com.babylion.team1.domain.conversation.entity.MessageRole;
import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.entity.FeedbackEvidence;
import com.babylion.team1.domain.feedback.entity.FeedbackReaction;
import com.babylion.team1.domain.feedback.entity.ReactionType;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EntityMappingTest {

	@Autowired
	EntityManager em;

	@Test
	void v3_ERD_엔티티를_저장하고_다시_읽을_수_있다() {
		User leader = User.builder().email("leader@test.com").passwordHash("hash").name("팀장").build();
		em.persist(leader);

		Team team = Team.builder().name("기획팀").description("설명").createdBy(leader).build();
		em.persist(team);
		em.persist(TeamMember.leader(team, leader));
		TeamSettings settings = TeamSettings.createDefault(team);
		em.persist(settings);
		TeamTaskType taskType = TeamTaskType.builder().team(team).name("주간 보고서 작성").successDefinition("수정 없이 제출").build();
		em.persist(taskType);

		Upload upload = Upload.builder().team(team).uploader(leader).fileName("conversations.json").conversationCount(3).build();
		em.persist(upload);

		Conversation conversation = Conversation.builder()
			.team(team).upload(upload).user(leader).taskType(taskType)
			.externalId("ext-1").title("보고서").model("gpt-4o")
			.startedAt(LocalDateTime.now()).conversationDate(LocalDate.now())
			.turnCount(4).estimatedTokens(1200).isSuccess(true)
			.build();
		em.persist(conversation);
		em.persist(Message.builder().conversation(conversation).idx(0).role(MessageRole.USER).content("보고서 써줘").build());

		Feedback feedback = Feedback.builder()
			.team(team).upload(upload).user(leader).taskType(taskType)
			.feedbackDate(LocalDate.now()).type("VAGUE_REQUEST").title("요청이 모호함")
			.problem("문제").causes(List.of("형식 미지정", "대상 독자 누락")).improvement("개선")
			.occurrenceCount(2)
			.build();
		em.persist(feedback);
		em.persist(FeedbackEvidence.builder().feedback(feedback).conversation(conversation).turnIndexes(List.of(0, 2)).build());
		em.persist(FeedbackReaction.builder().feedback(feedback).user(leader).reaction(ReactionType.HELPFUL).build());

		em.flush();
		em.clear();

		TeamSettings foundSettings = em.find(TeamSettings.class, settings.getId());
		assertThat(foundSettings.getMinDailyConversations()).isEqualTo(TeamSettings.DEFAULT_MIN_DAILY_CONVERSATIONS);
		assertThat(foundSettings.isTargetSet()).isFalse();

		Upload foundUpload = em.find(Upload.class, upload.getId());
		assertThat(foundUpload.getStatus()).isEqualTo(UploadStatus.UPLOADED);
		assertThat(foundUpload.getProgress()).isZero();

		Feedback foundFeedback = em.find(Feedback.class, feedback.getId());
		assertThat(foundFeedback.getCauses()).containsExactly("형식 미지정", "대상 독자 누락");
		assertThat(foundFeedback.getIsShared()).isFalse();
		assertThat(foundFeedback.getIsGuide()).isFalse();

		List<Integer> turnIndexes = em.createQuery(
				"select e.turnIndexes from FeedbackEvidence e where e.feedback.id = :id", List.class)
			.setParameter("id", feedback.getId())
			.getSingleResult();
		assertThat(turnIndexes).containsExactly(0, 2);
	}

	@Test
	void v4_분석_항목을_저장하고_분석_작업과_대화_버전을_조회할_수_있다() {
		User owner = User.builder().email("analysis-v4@test.com").passwordHash("hash").name("분석 요청자").build();
		em.persist(owner);
		Team team = Team.builder().name("분석팀").createdBy(owner).build();
		em.persist(team);
		TeamSettings settings = TeamSettings.createDefault(team);
		em.persist(settings);
		TeamTaskType taskType = TeamTaskType.builder().team(team).name("보고서").successDefinition("완료").build();
		em.persist(taskType);
		Upload upload = Upload.builder().team(team).uploader(owner).fileName("analysis.json").conversationCount(1).build();
		em.persist(upload);

		LocalDate date = LocalDate.of(2026, 11, 24);
		Conversation conversation = Conversation.builder()
			.team(team).upload(upload).user(owner).taskType(taskType).externalId("v4-conversation")
			.startedAt(date.atStartOfDay()).conversationDate(date).turnCount(2).estimatedTokens(10).isSuccess(false)
			.build();
		em.persist(conversation);
		ConversationVersion version = ConversationVersion.builder()
			.conversation(conversation).sourceUpload(upload).inputVersion(1).contentHash("a".repeat(64))
			.startedAt(Instant.parse("2026-11-23T15:00:00Z")).conversationDate(date)
			.turnCount(2).estimatedTokens(10).build();
		em.persist(version);
		Analysis analysis = Analysis.builder()
			.upload(upload).team(team).user(owner).settings(settings)
			.idempotencyKey("analysis-v4").requestHash("b".repeat(64)).selectedCount(1).build();
		em.persist(analysis);
		AnalysisItem item = AnalysisItem.builder()
			.analysis(analysis).version(version).team(team).user(owner).taskType(taskType)
			.conversationDate(date).aiEvidenceMessageKeys(List.of("message-1")).build();
		em.persist(item);
		em.flush();
		em.clear();

		AnalysisItem found = em.find(AnalysisItem.class, item.getId());
		assertThat(found.getAnalysis().getId()).isEqualTo(analysis.getId());
		assertThat(found.getAnalysis().getUpload().getId()).isEqualTo(upload.getId());
		assertThat(found.getAnalysis().getSettings().getId()).isEqualTo(settings.getId());
		assertThat(found.getAnalysis().getStatus()).isEqualTo(AnalysisStatus.QUEUED);
		assertThat(found.getAnalysis().getCompletedCount()).isZero();
		assertThat(found.getStatus()).isEqualTo(AnalysisItemStatus.QUEUED);
		assertThat(found.getVersion().getConversation().getId()).isEqualTo(conversation.getId());
		assertThat(found.getVersion().getSourceUpload().getId()).isEqualTo(upload.getId());
		assertThat(found.getVersion().getStartedAt()).isEqualTo(version.getStartedAt());
		assertThat(found.getAiEvidenceMessageKeys()).containsExactly("message-1");
	}

	@Test
	void 공유되지_않은_피드백은_가이드로_지정할_수_없고_공유_해제하면_가이드에서도_빠진다() {
		Feedback feedback = Feedback.builder().title("t").build();

		assertThatThrownBy(feedback::addToGuide)
			.isInstanceOf(CustomException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.FEEDBACK_NOT_SHARED);

		feedback.share();
		feedback.addToGuide();
		assertThat(feedback.getIsGuide()).isTrue();

		feedback.unshare();
		assertThat(feedback.getIsShared()).isFalse();
		assertThat(feedback.getIsGuide()).isFalse();
		assertThat(feedback.getGuideAddedAt()).isNull();
	}
}
