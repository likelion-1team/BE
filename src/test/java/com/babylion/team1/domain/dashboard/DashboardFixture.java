package com.babylion.team1.domain.dashboard;

import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.jwt.JwtProvider;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 대시보드 통합 테스트용 데이터 생성 도구. 테스트 클래스에 {@code @Import(DashboardFixture.class)}로 붙여 쓴다.
 * 저장은 EntityManager.persist로 하므로 테스트의 트랜잭션(@Transactional) 안에서 호출해야 한다.
 * conversation·feedback은 기본값을 채운 builder를 돌려주므로, 테스트에 필요한 값만 덮어쓰고 save로 저장한다.
 */
@TestComponent
public class DashboardFixture {

    private final EntityManager em;
    private final JwtProvider jwtProvider;
    private final AtomicInteger externalIdSeq = new AtomicInteger();

    public DashboardFixture(EntityManager em, JwtProvider jwtProvider) {
        this.em = em;
        this.jwtProvider = jwtProvider;
    }

    public User user(String name, String email) {
        return persist(User.builder().name(name).email(email).passwordHash("hashed-password").build());
    }

    public Team team(String name, User createdBy) {
        return persist(Team.builder().name(name).description("설명").createdBy(createdBy).build());
    }

    public TeamTaskType taskType(Team team, String name) {
        return taskType(team, name, "성공 정의");
    }

    public TeamTaskType taskType(Team team, String name, String successDefinition) {
        return persist(TeamTaskType.builder().team(team).name(name).successDefinition(successDefinition).build());
    }

    public Upload upload(Team team, User uploader) {
        return persist(Upload.builder().team(team).uploader(uploader).fileName("conversations.json")
                .conversationCount(10).build());
    }

    /** 그날 10:00에 시작한 대화 */
    public Conversation.ConversationBuilder conversation(Upload upload, User user, TeamTaskType taskType,
            LocalDate date) {
        return conversation(upload, user, taskType, date.atTime(10, 0));
    }

    /**
     * 기본값: 업로드의 팀, 제목 "대화", gpt-4o, 4턴, 1000토큰, 호출비 NULL, 성공, 재작업 없음, 검토·수습 0초.
     * externalId는 겹치지 않게 순서대로 붙인다.
     */
    public Conversation.ConversationBuilder conversation(Upload upload, User user, TeamTaskType taskType,
            LocalDateTime startedAt) {
        return Conversation.builder()
                .team(upload.getTeam()).upload(upload).user(user).taskType(taskType)
                .externalId("dashboard-ext-" + externalIdSeq.incrementAndGet()).title("대화").model("gpt-4o")
                .startedAt(startedAt).conversationDate(startedAt.toLocalDate())
                .turnCount(4).estimatedTokens(1000)
                .isSuccess(true);
    }

    public Conversation save(Conversation.ConversationBuilder builder) {
        return persist(builder.build());
    }

    /** 기본값: 업로드의 팀, VAGUE_REQUEST, 제목 "피드백", 1회, 예상 절감 0 */
    public Feedback.FeedbackBuilder feedback(Upload upload, User user, TeamTaskType taskType, LocalDate date) {
        return Feedback.builder()
                .team(upload.getTeam()).upload(upload).user(user).taskType(taskType)
                .feedbackDate(date).type("VAGUE_REQUEST").title("피드백").problem("문제").improvement("개선")
                .occurrenceCount(1);
    }

    public Feedback save(Feedback.FeedbackBuilder builder) {
        return persist(builder.build());
    }

    /** user로 로그인한 GET 요청 */
    public MockHttpServletRequestBuilder get(User user, String url) {
        return MockMvcRequestBuilders.get(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId()));
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
