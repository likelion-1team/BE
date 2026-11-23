package com.babylion.team1.domain.feedback.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.repository.FeedbackRepository;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.jwt.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedbackGuideControllerIntegrationTest {

    private static final String GUIDE = "/api/v1/feedbacks/{feedbackId}/guide";
    private static final String GUIDES = "/api/v1/teams/{teamId}/guides";
    private static final String SHARED_CASES = "/api/v1/teams/{teamId}/shared-cases";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void assignsGuideAndKeepsStoredTimestampOnRetry() throws Exception {
        Fixture fixture = saveFixture(true);
        MvcResult initial = assign(fixture)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("가이드 지정 성공"))
                .andExpect(jsonPath("$.data.isGuide").value(true))
                .andExpect(jsonPath("$.data.guideAddedAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andReturn();
        String addedAt = field(initial, "$.data.guideAddedAt");
        entityManager.flush();
        entityManager.clear();

        Feedback persisted = feedbackRepository.findById(fixture.feedbackId()).orElseThrow();
        assertThat(persisted.getIsGuide()).isTrue();
        assertThat(persisted.guideAddedAtInKst()).isEqualTo(OffsetDateTime.parse(addedAt));
        assign(fixture).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isGuide").value(true))
                .andExpect(jsonPath("$.data.guideAddedAt").value(addedAt));

        mockMvc.perform(get(GUIDES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups[0].guides[0].feedbackId").value(fixture.feedbackId()))
                .andExpect(jsonPath("$.data.groups[0].guides[0].guideAddedAt").value(addedAt));
        mockMvc.perform(get(SHARED_CASES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].isGuide").value(true));
    }

    @Test
    void rejectsUnsharedFeedback() throws Exception {
        Fixture fixture = saveFixture(false);

        assign(fixture).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_SHARED"));
        assertThat(feedbackRepository.findById(fixture.feedbackId()).orElseThrow().getIsGuide()).isFalse();
    }

    @Test
    void checksRoleInTheFeedbackTeamEvenForItsOwner() throws Exception {
        Fixture fixture = saveFixture(true);
        Fixture other = saveFixture(true);

        mockMvc.perform(put(GUIDE, fixture.feedbackId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_ONLY"));
        mockMvc.perform(put(GUIDE, fixture.feedbackId()).header(HttpHeaders.AUTHORIZATION, bearer(other.leaderId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
        assertThat(feedbackRepository.findById(fixture.feedbackId()).orElseThrow().getIsGuide()).isFalse();
    }

    @Test
    void reportsMissingFeedbackAndRequiresAccessToken() throws Exception {
        Fixture fixture = saveFixture(true);
        mockMvc.perform(put(GUIDE, Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEEDBACK_NOT_FOUND"));
        mockMvc.perform(put(GUIDE, fixture.feedbackId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(put(GUIDE, fixture.feedbackId()).header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void exposesGuidePutInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/guide'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/feedbacks/{feedbackId}/guide'].put.requestBody").doesNotExist());
    }

    @Test
    void removeGuidePreservesSharedFeedbackAndAllowsReassignment() throws Exception {
        Fixture fixture = saveFixture(true);
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
        assertThat(after.getIsGuide()).isFalse();
        assertThat(after.getGuideAddedAt()).isNull();
        remove(fixture.feedbackId(), fixture.leaderId())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get(GUIDES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups").isEmpty());
        mockMvc.perform(get(SHARED_CASES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].feedbackId").value(fixture.feedbackId()))
                .andExpect(jsonPath("$.data.items[0].isGuide").value(false));

        assign(fixture).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isGuide").value(true))
                .andExpect(jsonPath("$.data.guideAddedAt", endsWith("+09:00")));
        mockMvc.perform(get(GUIDES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups[0].guides[0].feedbackId").value(fixture.feedbackId()));
    }

    @Test
    void removeGuideReturnsNoContentAfterSharingWasRevoked() throws Exception {
        Fixture fixture = saveFixture(true);
        assign(fixture).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/feedbacks/{feedbackId}/share", fixture.feedbackId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shared\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isShared").value(false));

        remove(fixture.feedbackId(), fixture.leaderId())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        entityManager.flush();
        entityManager.clear();
        assertRevoked(fixture);
    }

    @Test
    void removeGuideRequiresFeedbackAndItsTeamLeader() throws Exception {
        Fixture fixture = saveFixture(true);
        Fixture other = saveFixture(true);
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
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(feedbackRepository.findById(fixture.feedbackId()).orElseThrow().getIsGuide()).isTrue();
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
    void twoConcurrentAssignmentsReturnTheSameStoredTimestamp() throws Exception {
        Fixture fixture = transactions.execute(ignored -> saveFixture(true));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier start = new CyclicBarrier(2);
            Callable<MvcResult> assignConcurrently = () -> {
                start.await(5, TimeUnit.SECONDS);
                return assign(fixture).andExpect(status().isOk()).andReturn();
            };
            List<Future<MvcResult>> assignments = executor.invokeAll(
                    List.of(assignConcurrently, assignConcurrently), 20, TimeUnit.SECONDS);

            String first = field(assignments.get(0).get(), "$.data.guideAddedAt");
            String second = field(assignments.get(1).get(), "$.data.guideAddedAt");
            assertThat(first).isEqualTo(second);
            Feedback persisted = feedbackRepository.findById(fixture.feedbackId()).orElseThrow();
            assertThat(persisted.getIsGuide()).isTrue();
            assertThat(persisted.guideAddedAtInKst()).isEqualTo(OffsetDateTime.parse(first));
        } finally {
            closeFixture(executor, fixture);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void assignmentReadsRevokedStateAfterWaitingForTheFeedbackLock() throws Exception {
        Fixture fixture = transactions.execute(ignored -> saveFixture(true));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch revocationFlushed = new CountDownLatch(1);
        CountDownLatch commitRevocation = new CountDownLatch(1);
        try {
            Future<?> revoke = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
                feedbackRepository.findByIdForUpdate(fixture.feedbackId()).orElseThrow().unshare();
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
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(field(result, "$.code")).isEqualTo("FEEDBACK_NOT_SHARED");
            assertRevoked(fixture);
        } finally {
            commitRevocation.countDown();
            closeFixture(executor, fixture);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void revocationAfterAssignmentClearsBothLists() throws Exception {
        Fixture fixture = transactions.execute(ignored -> saveFixture(true));
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
                feedbackRepository.findByIdForUpdate(fixture.feedbackId()).orElseThrow().unshare();
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

    private Fixture saveFixture(boolean shared) {
        User leader = saveUser("가이드 검증 팀장");
        User owner = saveUser("가이드 검증 작성자");
        Team team = Team.builder().name("가이드 검증팀").createdBy(leader).build();
        entityManager.persist(team);
        entityManager.persist(TeamMember.leader(team, leader));
        entityManager.persist(TeamMember.member(team, owner));
        TeamTaskType type = TeamTaskType.builder().team(team).name("주간 보고서").successDefinition("검토 통과").build();
        entityManager.persist(type);
        Upload upload = Upload.builder().team(team).uploader(owner).fileName("conversations.json").conversationCount(1)
                .build();
        upload.startAnalysis(1);
        upload.complete();
        entityManager.persist(upload);
        Feedback feedback = Feedback.builder().team(team).user(owner).taskType(type).upload(upload)
                .feedbackDate(LocalDate.of(2026, 11, 21)).type("MISSING_CONTEXT").title("보고서 가이드")
                .problem("분량 기준 없음").improvement("분량을 지정").occurrenceCount(1).build();
        if (shared) {
            feedback.share();
        }
        entityManager.persist(feedback);
        return new Fixture(team.getId(), leader.getId(), owner.getId(), feedback.getId());
    }

    private User saveUser(String name) {
        User user = User.builder().name(name).email("feedback-guide-it-" + UUID.randomUUID() + "@example.com")
                .passwordHash("test-password-hash").build();
        entityManager.persist(user);
        return user;
    }

    private ResultActions assign(Fixture fixture) throws Exception {
        return mockMvc.perform(put(GUIDE, fixture.feedbackId())
                .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leaderId())));
    }

    private ResultActions remove(Long feedbackId, Long userId) throws Exception {
        return mockMvc.perform(delete(GUIDE, feedbackId).header(HttpHeaders.AUTHORIZATION, bearer(userId)));
    }

    private void assertRevoked(Fixture fixture) throws Exception {
        Feedback persisted = feedbackRepository.findById(fixture.feedbackId()).orElseThrow();
        assertThat(persisted.getIsShared()).isFalse();
        assertThat(persisted.getIsGuide()).isFalse();
        assertThat(persisted.getGuideAddedAt()).isNull();
        mockMvc.perform(get(GUIDES, fixture.teamId()).header(HttpHeaders.AUTHORIZATION, bearer(fixture.ownerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups").isEmpty());
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

    private String field(MvcResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtProvider.createAccessToken(userId);
    }

    private record Fixture(Long teamId, Long leaderId, Long ownerId, Long feedbackId) {}
}
