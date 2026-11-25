package com.babylion.team1.domain.team.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamGoal;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.repository.TeamGoalRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.support.TestAccessTokens;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class TeamGoalControllerIntegrationTest {

    private static final String GOALS = "/api/v1/teams/{teamId}/goals";
    private static final String INITIAL_REQUEST = """
            {"expectedVersion":0,"successRateMin":0.8,"errorReworkRateMax":0.2,"tokensPerSuccessMax":5000}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TeamGoalRepository teamGoalRepository;
    @Autowired
    private TeamSettingsRepository teamSettingsRepository;
    @Autowired
    private TestAccessTokens accessTokens;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void teamCreationPersistsVersionZeroAndBothRolesCanReadIt() throws Exception {
        User leader = saveUser();
        String leaderToken = bearer(leader.getId());
        MvcResult result = mockMvc.perform(post("/api/v1/teams")
                        .header(HttpHeaders.AUTHORIZATION, leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"팀 목표 테스트\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long teamId = JsonPath.parse(result.getResponse().getContentAsString()).read("$.data.teamId", Long.class);
        User member = saveUser();
        entityManager.persist(TeamMember.member(entityManager.getReference(Team.class, teamId), member));
        entityManager.flush();
        entityManager.clear();

        TeamGoal goal = teamGoalRepository.findByTeamId(teamId).orElseThrow();
        assertThat(goal.getVersion()).isZero();
        assertThat(goal.getUpdatedAt()).isNull();
        expectUnset(read(teamId, leaderToken));
        expectUnset(read(teamId, bearer(member.getId())));
    }

    @Test
    void replacesGoalsInFullAndLeavesAnalysisSettingsUntouched() throws Exception {
        Fixture fixture = saveFixture(true);
        TeamSettings priorSettings = teamSettingsRepository.findByTeamId(fixture.teamId()).orElseThrow();
        MvcResult saved = update(fixture.teamId(), fixture.leaderToken(), INITIAL_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("목표 저장 성공"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.successRateMin").value(0.8))
                .andExpect(jsonPath("$.data.errorReworkRateMax").value(0.2))
                .andExpect(jsonPath("$.data.tokensPerSuccessMax").value(5000))
                .andExpect(jsonPath("$.data.updatedAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andReturn();
        String updatedAt = JsonPath.parse(saved.getResponse().getContentAsString()).read("$.data.updatedAt");
        assertThat(teamGoalRepository.findByTeamId(fixture.teamId()).orElseThrow().getUpdatedAt())
                .isEqualTo(OffsetDateTime.parse(updatedAt).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
        read(fixture.teamId(), bearer(fixture.memberId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.updatedAt").value(updatedAt));

        update(fixture.teamId(), fixture.leaderToken(), "{\"expectedVersion\":1,\"successRateMin\":0.85}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.successRateMin").value(0.85))
                .andExpect(jsonPath("$.data.errorReworkRateMax").value(nullValue()))
                .andExpect(jsonPath("$.data.tokensPerSuccessMax").value(nullValue()));

        update(fixture.teamId(), fixture.leaderToken(), "{\"expectedVersion\":2,\"successRateMin\":0.85}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(3));

        update(fixture.teamId(), fixture.leaderToken(), INITIAL_REQUEST)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errors.currentVersion").value(3));
        read(fixture.teamId(), fixture.leaderToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.successRateMin").value(0.85));

        TeamSettings currentSettings = teamSettingsRepository.findByTeamId(fixture.teamId()).orElseThrow();
        assertThat(currentSettings.getId()).isEqualTo(priorSettings.getId());
        assertThat(currentSettings.getUpdatedAt()).isEqualTo(priorSettings.getUpdatedAt());
        assertThat(currentSettings.getTargetCount()).isEqualTo(priorSettings.getTargetCount());
    }

    @Test
    void acceptsRateBoundariesAndReturnsThePersistedPrecision() throws Exception {
        Fixture fixture = saveFixture(true);
        update(fixture.teamId(), fixture.leaderToken(), """
                {"expectedVersion":0,"successRateMin":0.81235,"errorReworkRateMax":1,"tokensPerSuccessMax":1}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successRateMin").value(0.8124))
                .andExpect(jsonPath("$.data.errorReworkRateMax").value(1))
                .andExpect(jsonPath("$.data.tokensPerSuccessMax").value(1));
        update(fixture.teamId(), fixture.leaderToken(), "{\"expectedVersion\":1,\"successRateMin\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successRateMin").value(0));
    }

    @Test
    void existingTeamWithoutStoredGoalsCanReadAndSaveFromVersionZero() throws Exception {
        Fixture fixture = saveFixture(false);
        expectUnset(read(fixture.teamId(), fixture.leaderToken()));
        update(fixture.teamId(), fixture.leaderToken(), "{\"expectedVersion\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.currentVersion").value(0));
        assertThat(teamGoalRepository.findByTeamId(fixture.teamId())).isEmpty();
        update(fixture.teamId(), fixture.leaderToken(), INITIAL_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        assertThat(teamGoalRepository.findByTeamId(fixture.teamId())).isPresent();
    }

    @Test
    void onlyTheTargetTeamLeaderCanSaveAndOnlyMembersCanRead() throws Exception {
        Fixture fixture = saveFixture(true);
        String memberToken = bearer(fixture.memberId());
        String outsiderToken = bearer(saveUser().getId());
        expectUnset(read(fixture.teamId(), memberToken));
        update(fixture.teamId(), memberToken, INITIAL_REQUEST)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_FORBIDDEN"));
        read(fixture.teamId(), outsiderToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        update(fixture.teamId(), outsiderToken, INITIAL_REQUEST)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        read(Long.MAX_VALUE, fixture.leaderToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get(GOALS, fixture.teamId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(put(GOALS, fixture.teamId())
                        .contentType(MediaType.APPLICATION_JSON).content(INITIAL_REQUEST))
                .andExpect(status().isUnauthorized());
        expectUnset(read(fixture.teamId(), fixture.leaderToken()));
    }

    @Test
    void rejectsInvalidVersionsRatesAndTokenLimitsAs422() throws Exception {
        Fixture fixture = saveFixture(true);
        for (String invalid : List.of(
                "{}",
                "{\"expectedVersion\":null}",
                "{\"expectedVersion\":-1}",
                "{\"expectedVersion\":0.4}",
                "{\"expectedVersion\":0,\"successRateMin\":1.01}",
                "{\"expectedVersion\":0,\"errorReworkRateMax\":-0.01}",
                "{\"expectedVersion\":0,\"tokensPerSuccessMax\":0}",
                "{\"expectedVersion\":0,\"tokensPerSuccessMax\":4.5}",
                "{\"expectedVersion\":0,\"tokensPerSuccessMax\":2147483648}")) {
            update(fixture.teamId(), fixture.leaderToken(), invalid)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        expectUnset(read(fixture.teamId(), fixture.leaderToken()));
    }

    @Test
    void exposesGetAndPutInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/goals'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/goals'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/goals'].put.requestBody.required").value(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentUpdatesOfTheSameVersionHaveOneWinner(boolean storedGoals) throws Exception {
        Fixture fixture = transactions.execute(ignored -> saveFixture(storedGoals));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier start = new CyclicBarrier(2);
            Future<MvcResult> first = executor.submit(() -> saveConcurrently(fixture, start, 100));
            Future<MvcResult> second = executor.submit(() -> saveConcurrently(fixture, start, 200));
            MvcResult firstResult = first.get(20, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(20, TimeUnit.SECONDS);

            assertThat(List.of(firstResult.getResponse().getStatus(), secondResult.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 409);
            boolean firstWon = firstResult.getResponse().getStatus() == 200;
            MvcResult conflict = firstWon ? secondResult : firstResult;
            Integer currentVersion = JsonPath.parse(conflict.getResponse().getContentAsString())
                    .read("$.errors.currentVersion", Integer.class);
            assertThat(currentVersion).isEqualTo(1);
            TeamGoal stored = teamGoalRepository.findByTeamId(fixture.teamId()).orElseThrow();
            assertThat(stored.getVersion()).isEqualTo(1);
            assertThat(stored.getTokensPerSuccessMax()).isEqualTo(firstWon ? 100 : 200);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            transactions.executeWithoutResult(ignored -> {
                jdbc.update("delete from team_goals where team_id = ?", fixture.teamId());
                jdbc.update("delete from team_settings where team_id = ?", fixture.teamId());
                jdbc.update("delete from team_members where team_id = ?", fixture.teamId());
                jdbc.update("delete from teams where id = ?", fixture.teamId());
                jdbc.update("delete from auth_sessions where user_id in (?, ?)", fixture.leaderId(), fixture.memberId());
                jdbc.update("delete from users where id in (?, ?)", fixture.leaderId(), fixture.memberId());
            });
        }
    }

    private MvcResult saveConcurrently(Fixture fixture, CyclicBarrier start, int tokens) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return update(fixture.teamId(), fixture.leaderToken(),
                "{\"expectedVersion\":0,\"tokensPerSuccessMax\":%d}".formatted(tokens)).andReturn();
    }

    private ResultActions read(Long teamId, String token) throws Exception {
        return mockMvc.perform(get(GOALS, teamId).header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions update(Long teamId, String token, String body) throws Exception {
        return mockMvc.perform(put(GOALS, teamId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void expectUnset(ResultActions response) throws Exception {
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.successRateMin").value(nullValue()))
                .andExpect(jsonPath("$.data.errorReworkRateMax").value(nullValue()))
                .andExpect(jsonPath("$.data.tokensPerSuccessMax").value(nullValue()))
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()));
    }

    private Fixture saveFixture(boolean createGoals) {
        User leader = saveUser();
        User member = saveUser();
        Team team = Team.builder().name("팀 목표 테스트").createdBy(leader).build();
        entityManager.persist(team);
        entityManager.persist(TeamMember.leader(team, leader));
        entityManager.persist(TeamMember.member(team, member));
        TeamSettings settings = TeamSettings.createDefault(team);
        settings.updateTarget(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null, 100);
        entityManager.persist(settings);
        if (createGoals) {
            entityManager.persist(TeamGoal.createDefault(team));
        }
        return new Fixture(team.getId(), leader.getId(), member.getId(), bearer(leader.getId()));
    }

    private User saveUser() {
        User user = User.builder().name("목표 검증 사용자")
                .email("team-goals-" + UUID.randomUUID() + "@example.com").passwordHash("test-hash").build();
        entityManager.persist(user);
        return user;
    }

    private String bearer(Long userId) {
        return "Bearer " + accessTokens.create(userId);
    }

    private record Fixture(Long teamId, Long leaderId, Long memberId, String leaderToken) {}
}
