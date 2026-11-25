package com.babylion.team1.domain.deletion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.analysis.entity.AnalysisItem;
import com.babylion.team1.domain.analysis.entity.AnalysisOutcome;
import com.babylion.team1.domain.analysis.entity.AnalysisReworkType;
import com.babylion.team1.domain.analysis.entity.OutcomeAssessment;
import com.babylion.team1.domain.analysis.entity.ReworkAssessment;
import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.conversation.entity.ConversationVersion;
import com.babylion.team1.domain.conversation.entity.Message;
import com.babylion.team1.domain.conversation.entity.MessageRole;
import com.babylion.team1.domain.conversation.repository.MessageRepository;
import com.babylion.team1.domain.dashboard.DashboardFixture;
import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Content;
import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Example;
import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.entity.FeedbackEvidence;
import com.babylion.team1.domain.feedback.entity.FeedbackReaction;
import com.babylion.team1.domain.feedback.entity.FeedbackUsageEvent;
import com.babylion.team1.domain.feedback.entity.Guide;
import com.babylion.team1.domain.feedback.entity.ReactionType;
import com.babylion.team1.domain.feedback.entity.SharePreview;
import com.babylion.team1.domain.feedback.entity.SharedCase;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.support.TestAccessTokens;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커밋 뒤에 워커가 실행되므로 테스트에 트랜잭션을 걸지 않고 데이터를 직접 지운다.
 * 기본은 워커를 막아 "요청 직후" 상태를 확인하고, 실제 삭제가 필요한 테스트만 runWorker로 실행한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DashboardFixture.class)
class DeletionIntegrationTest {

    private static final String CONVERSATION = "/api/v1/conversations/{conversationId}";
    private static final String JOB = "/api/v1/deletion-jobs/{deletionJobId}";
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private DashboardFixture fixture;
    @Autowired
    private TestAccessTokens accessTokens;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DeletionWorker worker;
    @MockitoSpyBean
    private DeletionProcessor processor;
    @MockitoSpyBean
    private MessageRepository messages;

    private User owner;
    private User leader;
    private Team team;
    private TeamTaskType taskType;
    private TeamSettings settings;
    private Source source;

    @BeforeEach
    void setUp() {
        doNothing().when(processor).process(anyLong());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID() + "@test.com";
            leader = fixture.user("팀장", "leader-" + suffix);
            owner = fixture.user("직원", "owner-" + suffix);
            team = fixture.team("삭제 테스트 팀", leader);
            entityManager.persist(TeamMember.leader(team, leader));
            entityManager.persist(TeamMember.member(team, owner));
            taskType = fixture.taskType(team, "주간 보고서");
            settings = fixture.settings(team, taskType, DATE.minusDays(10), DATE.plusDays(10));
            source = saveAnalyzedConversation();
        });
    }

    @AfterEach
    void cleanUp() {
        Long teamId = team.getId();
        jdbc.update("delete from deletion_jobs where user_id in (?, ?)", owner.getId(), leader.getId());
        jdbc.update("delete from guides where team_id = ?", teamId);
        jdbc.update("delete from shared_cases where team_id = ?", teamId);
        for (String table : List.of("share_previews", "feedback_usage_events", "feedback_reactions",
                "feedback_evidences")) {
            jdbc.update("delete from " + table + " where feedback_id in (select id from feedbacks where team_id = ?)",
                    teamId);
        }
        jdbc.update("delete from feedbacks where team_id = ?", teamId);
        for (String table : List.of("outcome_assessments", "rework_assessments")) {
            jdbc.update("delete from " + table + " where item_id in (select id from analysis_items where team_id = ?)",
                    teamId);
        }
        jdbc.update("delete from analysis_items where team_id = ?", teamId);
        jdbc.update("update conversations set current_version_id = null where team_id = ?", teamId);
        for (String table : List.of("messages", "conversation_versions")) {
            jdbc.update("delete from " + table
                    + " where conversation_id in (select id from conversations where team_id = ?)", teamId);
        }
        for (String table : List.of("conversations", "analyses", "uploads", "team_settings", "team_task_types",
                "team_members")) {
            jdbc.update("delete from " + table + " where team_id = ?", teamId);
        }
        jdbc.update("delete from teams where id = ?", teamId);
        jdbc.update("delete from users where id in (?, ?)", owner.getId(), leader.getId());
    }

    @Test
    @DisplayName("다른 사람의 대화와 없는 대화는 같은 404로 응답하고 아무것도 바꾸지 않는다")
    void hidesOthersConversations() throws Exception {
        requestDeletion(source.versionId(), leader).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        requestDeletion(Long.MAX_VALUE, owner).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete(CONVERSATION, source.versionId())).andExpect(status().isUnauthorized());

        assertThat(count("deletion_jobs where version_id = ?", source.versionId())).isZero();
        assertThat(count("conversation_versions where id = ? and deleted_at is null", source.versionId())).isOne();
    }

    @Test
    @DisplayName("삭제 요청은 202와 작업 정보를 돌려주고, 다시 요청해도 같은 작업을 돌려준다")
    void returnsSameJobForRepeatedRequests() throws Exception {
        Long jobId = deletionJobId(requestDeletion(source.versionId(), owner)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("S202"))
                .andExpect(jsonPath("$.data.status").value("PENDING")));
        requestDeletion(source.versionId(), owner)
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/deletion-jobs/" + jobId))
                .andExpect(jsonPath("$.data.deletionJobId").value(jobId))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.statusUrl").value("/api/v1/deletion-jobs/" + jobId));

        jdbc.update("update deletion_jobs set status = 'RUNNING' where id = ?", jobId);
        requestDeletion(source.versionId(), owner)
                .andExpect(jsonPath("$.data.deletionJobId").value(jobId))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
        assertThat(count("deletion_jobs where version_id = ?", source.versionId())).isOne();
    }

    @Test
    @DisplayName("동시에 요청해도 작업은 하나만 만들어진다")
    void createsOneJobForConcurrentRequests() throws Exception {
        String token = bearer(owner);
        var requests = List.of(deleteAsync(token), deleteAsync(token));

        Long first = requests.get(0).get();
        assertThat(requests.get(1).get()).isEqualTo(first);
        assertThat(count("deletion_jobs where version_id = ?", source.versionId())).isOne();
    }

    @Test
    @DisplayName("요청 직후 원문·피드백·공유 사례·가이드·집계에서 사라진다")
    void blocksAccessBeforeWorkerRuns() throws Exception {
        // 피드백이 없는 대화를 남겨, 업무 유형 집계에서 삭제한 대화의 피드백만 빠지는지 본다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var other = fixture.save(fixture.conversation(fixture.consumedUpload(team, owner), owner, taskType, DATE));
            other.promote(fixture.version(other, 1, null));
        });
        sharedCases().andExpect(jsonPath("$.data.total").value(1));
        guides().andExpect(jsonPath("$.data.total").value(1));
        myFeedbacks().andExpect(jsonPath("$.data.total").value(1));
        kpis().andExpect(jsonPath("$.data.coverage.inScopeCount").value(1));
        topFeedbackTypes().andExpect(jsonPath("$.data.items[0].topFeedbackTypes[0]").value("MISSING_CONTEXT"));

        requestDeletion(source.versionId(), owner).andExpect(status().isAccepted());

        getAs(owner, CONVERSATION, source.conversationId()).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SOURCE_DELETED"));
        getAs(owner, "/api/v1/feedbacks/{id}", source.feedbackId()).andExpect(status().isGone());
        getAs(owner, "/api/v1/analyses/{id}/items", source.analysisId()).andExpect(jsonPath("$.data.total").value(0));
        myFeedbacks().andExpect(jsonPath("$.data.total").value(0));
        sharedCases().andExpect(jsonPath("$.data.total").value(0));
        guides().andExpect(jsonPath("$.data.total").value(0));
        kpis().andExpect(jsonPath("$.data.coverage.inScopeCount").value(0))
                .andExpect(jsonPath("$.data.coverage.selectedCount").value(0));
        topFeedbackTypes().andExpect(jsonPath("$.data.items[0].conversationCount").value(1))
                .andExpect(jsonPath("$.data.items[0].topFeedbackTypes").isEmpty());
        mockMvc.perform(authorized(post("/api/v1/feedbacks/{id}/usage-events", source.feedbackId()), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"%s\",\"eventType\":\"USE_CONFIRMED\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(authorized(post("/api/v1/feedbacks/{id}/share-preview", source.feedbackId()), owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());

        assertThat(count("shared_cases where feedback_id = ? and status = 'DELETED'", source.feedbackId())).isOne();
        assertThat(count("guides where team_id = ?", team.getId())).isZero();
        assertThat(count("analysis_items where id = ? and is_current = false", source.itemId())).isOne();
        // 실제 삭제는 아직 하지 않았다.
        assertThat(count("messages where version_id = ?", source.versionId())).isEqualTo(2);
    }

    @Test
    @DisplayName("워커가 끝나면 원문과 파생 데이터가 지워지고 메타데이터만 남는다")
    void workerPurgesSourceAndDerivedData() throws Exception {
        Long jobId = deletionJobId(requestDeletion(source.versionId(), owner));
        runWorker(jobId);

        assertThat(count("messages where version_id = ?", source.versionId())).isZero();
        for (String table : List.of("feedback_evidences", "feedback_reactions", "feedback_usage_events",
                "share_previews", "shared_cases")) {
            assertThat(count(table + " where feedback_id = ?", source.feedbackId())).as(table).isZero();
        }
        assertThat(count("feedbacks where id = ?", source.feedbackId())).isZero();
        assertThat(count("""
                analysis_items where id = ? and is_current = false and status = 'SUCCEEDED'
                and ai_outcome = 'SUCCESS' and ai_evidence_message_keys is null
                """, source.itemId())).isOne();
        assertThat(count("outcome_assessments where item_id = ? and note is null", source.itemId())).isOne();
        assertThat(count("rework_assessments where item_id = ? and note is null", source.itemId())).isOne();
        assertThat(count("""
                conversation_versions where id = ? and deleted_at is not null and title is null
                and excluded_attachments is null and content_hash is not null
                """, source.versionId())).isOne();
        assertThat(count("conversations where id = ? and title is null", source.conversationId())).isOne();

        getAs(owner, JOB, jobId).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.data.deletionJobId").value(jobId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.failureCode").isEmpty())
                .andExpect(jsonPath("$.data.length()").value(5));
        requestDeletion(source.versionId(), owner).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.deletionJobId").value(jobId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mockMvc.perform(authorized(get(CONVERSATION, source.conversationId()), owner)
                        .param("highlightFeedbackId", source.feedbackId().toString()))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("SOURCE_DELETED"));
    }

    @Test
    @DisplayName("요청이 커밋되면 워커가 자동으로 실행된다")
    void workerRunsAfterCommit() throws Exception {
        doCallRealMethod().when(processor).process(anyLong());
        Long jobId = deletionJobId(requestDeletion(source.versionId(), owner));

        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!"COMPLETED".equals(jobStatus(jobId)) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(jobStatus(jobId)).isEqualTo("COMPLETED");
        assertThat(count("messages where version_id = ?", source.versionId())).isZero();
    }

    @Test
    @DisplayName("현행 버전을 지워도 과거 버전은 집계에 되살아나지 않고 그 원문과 피드백은 남는다")
    void doesNotReviveOrPurgePreviousVersion() throws Exception {
        var latest = new TransactionTemplate(transactionManager).execute(status -> {
            entityManager.find(AnalysisItem.class, source.itemId()).retire();
            var conversation = entityManager.find(Conversation.class, source.conversationId());
            return saveAnalyzedVersion(conversation, 2);
        });
        Long jobId = deletionJobId(requestDeletion(latest.versionId(), owner));
        runWorker(jobId);

        kpis().andExpect(jsonPath("$.data.coverage.inScopeCount").value(0));
        assertThat(count("analysis_items where id = ? and is_current = false", source.itemId())).isOne();
        assertThat(count("feedbacks where id = ?", latest.feedbackId())).isZero();
        assertThat(count("messages where version_id = ?", latest.versionId())).isZero();
        assertThat(count("feedbacks where id = ?", source.feedbackId())).isOne();
        assertThat(count("messages where version_id = ?", source.versionId())).isEqualTo(2);
        assertThat(count("conversations where id = ? and title is not null", source.conversationId())).isOne();
    }

    @Test
    @DisplayName("삭제에 실패하면 FAILED로 남고 접근은 계속 막히며, 다시 요청하면 같은 작업을 이어서 처리한다")
    void failedJobCanBeRetriedWithoutExposingSource() throws Exception {
        Long jobId = deletionJobId(requestDeletion(source.versionId(), owner));
        doThrow(new IllegalStateException("삭제 실패")).when(messages).deleteByVersionId(source.versionId());
        runWorker(jobId);

        getAs(owner, JOB, jobId).andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureCode").value("DELETION_FAILED"))
                .andExpect(jsonPath("$.data.completedAt").isEmpty());
        assertThat(count("feedbacks where id = ?", source.feedbackId())).as("실패하면 전부 되돌린다").isOne();
        getAs(owner, CONVERSATION, source.conversationId()).andExpect(status().isGone());

        doNothing().when(processor).process(anyLong());
        requestDeletion(source.versionId(), owner).andExpect(jsonPath("$.data.deletionJobId").value(jobId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        reset(messages);
        runWorker(jobId);
        assertThat(jobStatus(jobId)).isEqualTo("COMPLETED");
        assertThat(count("messages where version_id = ?", source.versionId())).isZero();
    }

    @Test
    @DisplayName("오래 머문 작업만 스케줄러가 다시 처리한다")
    void schedulerRecoversStaleJobs() throws Exception {
        Long jobId = deletionJobId(requestDeletion(source.versionId(), owner));
        doCallRealMethod().when(processor).process(jobId);

        worker.recover();
        assertThat(jobStatus(jobId)).isEqualTo("PENDING");

        for (String stuck : List.of("RUNNING", "FAILED", "PENDING")) {
            jdbc.update("update deletion_jobs set status = ?, requested_at = timestampadd(minute, -11, requested_at)"
                    + " where id = ?", stuck, jobId);
            worker.recover();
            assertThat(jobStatus(jobId)).as(stuck).isEqualTo("COMPLETED");
        }
    }

    @Test
    @DisplayName("다른 사람의 삭제 작업과 없는 작업은 404로 응답한다")
    void hidesOthersJobs() throws Exception {
        Long jobId = deletionJobId(requestDeletion(source.versionId(), owner));

        getAs(leader, JOB, jobId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        getAs(owner, JOB, Long.MAX_VALUE).andExpect(status().isNotFound());
        mockMvc.perform(get(JOB, jobId)).andExpect(status().isUnauthorized());
    }

    private Source saveAnalyzedConversation() {
        var upload = fixture.consumedUpload(team, owner);
        var conversation = fixture.save(fixture.conversation(upload, owner, taskType, DATE));
        return saveAnalyzedVersion(conversation, 1);
    }

    private Source saveAnalyzedVersion(Conversation conversation, int inputVersion) {
        var version = ConversationVersion.builder().conversation(conversation)
                .sourceUpload(conversation.getUpload()).inputVersion(inputVersion)
                .contentHash(Integer.toString(inputVersion).repeat(64)).title("비공개 제목")
                .startedAt(Instant.parse("2026-11-20T01:00:00Z")).conversationDate(DATE).turnCount(2)
                .excludedAttachments(List.of("비공개.pdf")).build();
        entityManager.persist(version);
        conversation.promote(version);
        for (int index = 0; index < 2; index++) {
            entityManager.persist(Message.builder().conversation(conversation).version(version).idx(index)
                    .role(index == 0 ? MessageRole.USER : MessageRole.ASSISTANT).content("보호된 원문").build());
        }
        var item = fixture.save(fixture.item(settings, version).aiEvidenceMessageKeys(List.of("m_0")));
        Instant now = Instant.now();
        entityManager.persist(new OutcomeAssessment(item, 1, AnalysisOutcome.SUCCESS, "비공개 메모", now));
        entityManager.persist(new ReworkAssessment(item, 1, AnalysisReworkType.NONE, "비공개 메모", now));

        Feedback feedback = fixture.save(fixture.feedback(item.getAnalysis().getUpload(), owner, taskType, DATE)
                .type("MISSING_CONTEXT").exampleBefore("개선 전").exampleAfter("개선 후"));
        entityManager.persist(FeedbackEvidence.builder().feedback(feedback).conversation(conversation)
                .turnIndexes(List.of(0)).build());
        entityManager.persist(FeedbackReaction.builder().feedback(feedback).user(owner)
                .reaction(ReactionType.HELPFUL).build());
        entityManager.persist(new FeedbackUsageEvent(feedback, UUID.randomUUID().toString(),
                FeedbackUsageEvent.Type.COPY_SUCCEEDED));
        var preview = new SharePreview(feedback, new Content("공유 제목", "MISSING_CONTEXT", "문제", "개선",
                new Example("개선 전", "개선 후")), now);
        entityManager.persist(preview);
        var sharedCase = new SharedCase(feedback, preview, now);
        entityManager.persist(sharedCase);
        entityManager.persist(new Guide(sharedCase, leader, now));
        entityManager.flush();
        return new Source(conversation.getId(), version.getId(), item.getId(), item.getAnalysis().getId(),
                feedback.getId());
    }

    /** 실제 처리기를 호출한 스레드에서 바로 실행한다 */
    private void runWorker(Long jobId) {
        doCallRealMethod().when(processor).process(jobId);
        processor.process(jobId);
    }

    private CompletableFuture<Long> deleteAsync(String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return deletionJobId(mockMvc.perform(delete(CONVERSATION, source.versionId())
                        .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isAccepted()));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private ResultActions requestDeletion(Long versionId, User user) throws Exception {
        return mockMvc.perform(authorized(delete(CONVERSATION, versionId), user));
    }

    private ResultActions sharedCases() throws Exception {
        return getAs(leader, "/api/v1/teams/{teamId}/shared-cases", team.getId()).andExpect(status().isOk());
    }

    private ResultActions guides() throws Exception {
        return getAs(owner, "/api/v1/teams/{teamId}/guides", team.getId()).andExpect(status().isOk());
    }

    private ResultActions myFeedbacks() throws Exception {
        return getAs(owner, "/api/v1/teams/{teamId}/feedbacks", team.getId()).andExpect(status().isOk());
    }

    private ResultActions kpis() throws Exception {
        return mockMvc.perform(authorized(get("/api/v1/teams/{teamId}/dashboard/kpis", team.getId()), owner)
                        .param("scope", "ME").param("from", DATE.toString()).param("to", DATE.toString())
                        .param("settingsVersion", String.valueOf(settings.getVersion())))
                .andExpect(status().isOk());
    }

    private ResultActions topFeedbackTypes() throws Exception {
        return mockMvc.perform(authorized(get("/api/v1/teams/{teamId}/dashboard/task-types", team.getId()), leader)
                        .param("from", DATE.toString()).param("to", DATE.toString()))
                .andExpect(status().isOk());
    }

    private ResultActions getAs(User user, String path, Long id) throws Exception {
        return mockMvc.perform(authorized(get(path, id), user));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(user));
    }

    private String bearer(User user) {
        return "Bearer " + accessTokens.create(user.getId());
    }

    private Long deletionJobId(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.data.deletionJobId")).longValue();
    }

    private String jobStatus(Long jobId) {
        return jdbc.queryForObject("select status from deletion_jobs where id = ?", String.class, jobId);
    }

    private long count(String from, Long id) {
        return jdbc.queryForObject("select count(*) from " + from, Long.class, id);
    }

    private record Source(Long conversationId, Long versionId, Long itemId, Long analysisId, Long feedbackId) {
    }
}
