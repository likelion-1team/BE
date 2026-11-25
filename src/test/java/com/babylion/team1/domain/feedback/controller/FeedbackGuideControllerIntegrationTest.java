package com.babylion.team1.domain.feedback.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Content;
import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Example;
import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.entity.SharePreview;
import com.babylion.team1.domain.feedback.entity.SharedCase;
import com.babylion.team1.domain.feedback.repository.FeedbackRepository;
import com.babylion.team1.domain.feedback.repository.GuideRepository;
import com.babylion.team1.domain.feedback.service.FeedbackService;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.support.TestAccessTokens;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedbackGuideControllerIntegrationTest {

    private static final String GUIDE = "/api/v1/feedbacks/{feedbackId}/guide";
    private static final String ASSIGN_GUIDE = "/api/v1/teams/{teamId}/guides/{sharedCaseId}";
    private static final String GUIDES = "/api/v1/teams/{teamId}/guides";
    private static final String SHARED_CASES = "/api/v1/teams/{teamId}/shared-cases";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private GuideRepository guideRepository;
    @Autowired
    private FeedbackService feedbackService;
    @Autowired
    private TestAccessTokens accessTokens;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void assignsGuideAndReturnsTheSameResponseOnRetry() throws Exception {
        Fixture fixture = saveFixture();
        MvcResult initial = assign(fixture)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("가이드 지정 성공"))
                .andExpect(jsonPath("$.data.sharedCaseId").value(fixture.sharedCaseId()))
                .andExpect(jsonPath("$.data.isGuide").value(true))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andReturn();
        Map<String, Object> first = field(initial, "$.data");
        assertThat(first).containsOnlyKeys("sharedCaseId", "isGuide");
        entityManager.flush();
        entityManager.clear();

        var saved = guideRepository.findBySharedCaseId(fixture.sharedCaseId()).orElseThrow();
        MvcResult repeated = assign(fixture).andExpect(status().isOk()).andReturn();
        Map<String, Object> second = field(repeated, "$.data");
        assertThat(second).isEqualTo(first);
        entityManager.flush();
        entityManager.clear();

        var stored = guideRepository.findBySharedCaseId(fixture.sharedCaseId()).orElseThrow();
        assertThat(stored.getId()).isEqualTo(saved.getId());
        assertThat(stored.getDesignatedAt()).isEqualTo(saved.getDesignatedAt());
        mockMvc.perform(get(GUIDES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].sharedCaseId").value(fixture.sharedCaseId()))
                .andExpect(jsonPath("$.data.items[0].isGuide").value(true));
        mockMvc.perform(get(SHARED_CASES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].shareVersion").value(1))
                .andExpect(jsonPath("$.data.items[0].isGuide").value(true));
    }

    @Test
    void rejectsStaleVersionBeforeAndAfterSuccessfulAssignment() throws Exception {
        Fixture fixture = saveFixture();

        mockMvc.perform(assignment(fixture.teamId(), fixture.sharedCaseId(), 0)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(guideRepository.findBySharedCaseId(fixture.sharedCaseId())).isEmpty();

        assign(fixture).andExpect(status().isOk());
        mockMvc.perform(assignment(fixture.teamId(), fixture.sharedCaseId(), 0)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(guideRepository.findBySharedCaseId(fixture.sharedCaseId())).isPresent();
    }

    @Test
    void requiresExpectedShareVersion() throws Exception {
        Fixture fixture = saveFixture();

        for (String body : List.of("{}", "{\"expectedShareVersion\":null}")) {
            mockMvc.perform(put(ASSIGN_GUIDE, fixture.teamId(), fixture.sharedCaseId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("expectedShareVersion"));
        }
        assertThat(guideRepository.findBySharedCaseId(fixture.sharedCaseId())).isEmpty();
    }

    @Test
    void enforcesTeamRoleAndSharedCaseOwnership() throws Exception {
        Fixture fixture = saveFixture();
        Fixture other = saveFixture();

        mockMvc.perform(assignment(fixture.teamId(), fixture.sharedCaseId(), 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_FORBIDDEN"));
        mockMvc.perform(assignment(fixture.teamId(), fixture.sharedCaseId(), 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.leaderId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(assignment(fixture.teamId(), other.sharedCaseId(), 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(guideRepository.findBySharedCaseId(fixture.sharedCaseId())).isEmpty();
        assertThat(guideRepository.findBySharedCaseId(other.sharedCaseId())).isEmpty();
    }

    @Test
    void reportsMissingSharedCaseAndRequiresAccessToken() throws Exception {
        Fixture fixture = saveFixture();
        mockMvc.perform(assignment(fixture.teamId(), Long.MAX_VALUE, 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(assignment(fixture.teamId(), fixture.sharedCaseId(), 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(assignment(fixture.teamId(), fixture.sharedCaseId(), 1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void exposesOnlyTheSharedCaseBasedGuidePutInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/guides/{sharedCaseId}'].put.responses['200']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/guides/{sharedCaseId}'].put.requestBody.required")
                        .value(true))
                .andExpect(jsonPath("$.components.schemas.GuideAddRequest.required[0]").value("expectedShareVersion"))
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/guide'].put").doesNotExist());
    }

    @Test
    void removeGuidePreservesSharedFeedbackAndAllowsReassignment() throws Exception {
        Fixture fixture = saveFixture();
        assign(fixture).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        Feedback before = feedbackRepository.findById(fixture.feedbackId()).orElseThrow();
        LocalDateTime sharedAt = before.getSharedAt();
        String title = before.getTitle();

        remove(fixture.feedbackId(), fixture.leaderId())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        entityManager.flush();
        entityManager.clear();

        Feedback after = feedbackRepository.findById(fixture.feedbackId()).orElseThrow();
        assertThat(after.getIsShared()).isTrue();
        assertThat(after.getSharedAt()).isEqualTo(sharedAt);
        assertThat(after.getTitle()).isEqualTo(title);
        assertThat(after.getShareVersion()).isEqualTo(1);
        assertThat(guideRepository.findBySharedCaseId(fixture.sharedCaseId())).isEmpty();
        remove(fixture.feedbackId(), fixture.leaderId())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get(GUIDES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(get(SHARED_CASES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sharedCaseId").value(fixture.sharedCaseId()))
                .andExpect(jsonPath("$.data.items[0].isGuide").value(false));

        assign(fixture).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isGuide").value(true))
                .andExpect(jsonPath("$.data.sharedCaseId").value(fixture.sharedCaseId()));
        mockMvc.perform(get(GUIDES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sharedCaseId").value(fixture.sharedCaseId()));
    }

    @Test
    void removeGuideReturnsNoContentAfterSharingWasRevoked() throws Exception {
        Fixture fixture = saveFixture();
        assign(fixture).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/feedbacks/{feedbackId}/share", fixture.feedbackId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shared\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shared").value(false));

        remove(fixture.feedbackId(), fixture.leaderId())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        entityManager.flush();
        entityManager.clear();
        assertRevoked(fixture);
    }

    @Test
    void removeGuideRequiresFeedbackAndItsTeamLeader() throws Exception {
        Fixture fixture = saveFixture();
        Fixture other = saveFixture();
        assign(fixture).andExpect(status().isOk());

        remove(fixture.feedbackId(), fixture.ownerId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_ONLY"));
        remove(fixture.feedbackId(), other.leaderId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_ONLY"));
        remove(Long.MAX_VALUE, fixture.leaderId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
        mockMvc.perform(delete(GUIDE, fixture.feedbackId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        assertThat(guideRepository.findBySharedCaseId(fixture.sharedCaseId())).isPresent();
    }

    @Test
    void removeGuideExposesNoContentInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/guide'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/guide'].delete.responses['204'].content")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/guide'].delete.requestBody").doesNotExist());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoConcurrentAssignmentsReturnTheSameGuideAndStoreOneRow() throws Exception {
        Fixture fixture = transactions.execute(ignored -> saveFixture());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier start = new CyclicBarrier(2);
            Callable<MvcResult> assignConcurrently = () -> {
                start.await(5, TimeUnit.SECONDS);
                return assign(fixture).andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.sharedCaseId").value(fixture.sharedCaseId()))
                        .andExpect(jsonPath("$.data.isGuide").value(true))
                        .andReturn();
            };
            List<Future<MvcResult>> assignments = executor.invokeAll(
                    List.of(assignConcurrently, assignConcurrently), 20, TimeUnit.SECONDS);

            Map<String, Object> first = field(assignments.get(0).get(), "$.data");
            Map<String, Object> second = field(assignments.get(1).get(), "$.data");
            assertThat(first).isEqualTo(second);
            assertThat(jdbc.queryForObject("select count(*) from guides where shared_case_id = ?",
                    Long.class, fixture.sharedCaseId())).isEqualTo(1L);
        } finally {
            closeFixture(executor, fixture);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void assignmentReadsRevokedStateAfterWaitingForTheFeedbackLock() throws Exception {
        Fixture fixture = transactions.execute(ignored -> saveFixture());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch revocationFlushed = new CountDownLatch(1);
        CountDownLatch commitRevocation = new CountDownLatch(1);
        try {
            Future<?> revoke = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
                feedbackService.updateShare(fixture.feedbackId(), fixture.ownerId(), false, null, null, null);
                entityManager.flush();
                revocationFlushed.countDown();
                await(commitRevocation);
            }));
            await(revocationFlushed);

            CountDownLatch assignmentStarted = new CountDownLatch(1);
            Future<MvcResult> assignment = executor.submit(() -> {
                assignmentStarted.countDown();
                return assign(fixture).andReturn();
            });
            await(assignmentStarted);
            assertWaits(assignment);
            commitRevocation.countDown();

            revoke.get(10, TimeUnit.SECONDS);
            MvcResult result = assignment.get(10, TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            String error = field(result, "$.code");
            assertThat(error).isEqualTo("SHARING_REVOKED");
            assertRevoked(fixture);
        } finally {
            commitRevocation.countDown();
            closeFixture(executor, fixture);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void revocationAfterAssignmentClearsBothLists() throws Exception {
        Fixture fixture = transactions.execute(ignored -> saveFixture());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch assignmentFlushed = new CountDownLatch(1);
        CountDownLatch commitAssignment = new CountDownLatch(1);
        try {
            Future<MvcResult> assignment = executor.submit(() -> transactions.execute(ignored -> {
                MvcResult result;
                try {
                    result = assign(fixture).andExpect(status().isOk()).andReturn();
                } catch (Exception exception) {
                    throw new IllegalStateException("Guide request failed", exception);
                }
                entityManager.flush();
                assignmentFlushed.countDown();
                await(commitAssignment);
                return result;
            }));
            await(assignmentFlushed);

            CountDownLatch revocationStarted = new CountDownLatch(1);
            Future<?> revoke = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
                revocationStarted.countDown();
                feedbackService.updateShare(fixture.feedbackId(), fixture.ownerId(), false, null, null, null);
            }));
            await(revocationStarted);
            assertWaits(revoke);
            commitAssignment.countDown();

            assertThat(assignment.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            revoke.get(10, TimeUnit.SECONDS);
            assertRevoked(fixture);
        } finally {
            commitAssignment.countDown();
            closeFixture(executor, fixture);
        }
    }

    private Fixture saveFixture() {
        User leader = saveUser("가이드 검증 팀장");
        User owner = saveUser("가이드 검증 작성자");
        Team team = Team.builder().name("가이드 검증팀").createdBy(leader).build();
        entityManager.persist(team);
        entityManager.persist(TeamMember.leader(team, leader));
        entityManager.persist(TeamMember.member(team, owner));
        TeamTaskType type = TeamTaskType.builder().team(team).name("주간 보고서").successConditions(List.of("검토 통과"))
                .failureConditions(List.of()).unknownConditions(List.of()).build();
        entityManager.persist(type);
        Upload upload = Upload.builder().team(team).uploader(owner).fileName("conversations.json").conversationCount(1)
                .build();
        upload.startAnalysis(1);
        upload.complete();
        entityManager.persist(upload);
        Feedback feedback = Feedback.builder().team(team).user(owner).taskType(type).upload(upload)
                .feedbackDate(LocalDate.of(2026, 11, 21)).type("MISSING_CONTEXT").title("보고서 가이드")
                .problem("분량 기준 없음").improvement("분량을 지정").occurrenceCount(1).build();
        entityManager.persist(feedback);
        Instant now = Instant.now();
        var content = new Content("보고서 가이드", "MISSING_CONTEXT", "분량 기준 없음", "분량을 지정",
                new Example(null, null));
        var preview = new SharePreview(feedback, content, now);
        preview.consume(now);
        entityManager.persist(preview);
        var sharedCase = new SharedCase(feedback, preview, now);
        entityManager.persist(sharedCase);
        feedback.share();
        feedback.advanceShareVersion();
        return new Fixture(team.getId(), leader.getId(), owner.getId(), feedback.getId(), sharedCase.getId());
    }

    private User saveUser(String name) {
        User user = User.builder().name(name).email("feedback-guide-it-" + UUID.randomUUID() + "@example.com")
                .passwordHash("test-password-hash").build();
        entityManager.persist(user);
        return user;
    }

    private ResultActions assign(Fixture fixture) throws Exception {
        return mockMvc.perform(assignment(fixture.teamId(), fixture.sharedCaseId(), 1)
                .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())));
    }

    private MockHttpServletRequestBuilder assignment(Long teamId, Long sharedCaseId, int expectedShareVersion) {
        return put(ASSIGN_GUIDE, teamId, sharedCaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"expectedShareVersion":%d}
                        """.formatted(expectedShareVersion));
    }

    private ResultActions remove(Long feedbackId, Long userId) throws Exception {
        return mockMvc.perform(delete(GUIDE, feedbackId).header(HttpHeaders.AUTHORIZATION, bearer(userId)));
    }

    private void assertRevoked(Fixture fixture) throws Exception {
        Feedback persisted = feedbackRepository.findById(fixture.feedbackId()).orElseThrow();
        assertThat(persisted.getIsShared()).isFalse();
        assertThat(persisted.getShareVersion()).isEqualTo(2);
        assertThat(guideRepository.findBySharedCaseId(fixture.sharedCaseId())).isEmpty();
        mockMvc.perform(get(GUIDES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(get(SHARED_CASES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    private void closeFixture(ExecutorService executor, Fixture fixture) throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        transactions.executeWithoutResult(ignored -> {
            jdbc.update("delete from feedbacks where id = ?", fixture.feedbackId());
            jdbc.update("delete from uploads where team_id = ?", fixture.teamId());
            jdbc.update("delete from team_task_types where team_id = ?", fixture.teamId());
            jdbc.update("delete from team_members where team_id = ?", fixture.teamId());
            jdbc.update("delete from teams where id = ?", fixture.teamId());
            jdbc.update("delete from users where id in (?, ?)", fixture.leaderId(), fixture.ownerId());
        });
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating the test transaction", exception);
        }
    }

    private void assertWaits(Future<?> request) {
        assertThatThrownBy(() -> request.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    }

    private <T> T field(MvcResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }

    private String bearer(Long userId) {
        return "Bearer " + accessTokens.create(userId);
    }

    private record Fixture(Long teamId, Long leaderId, Long ownerId, Long feedbackId, Long sharedCaseId) {}
}
