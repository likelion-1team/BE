package com.babylion.team1.domain.team.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamGoal;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.repository.TeamGoalRepository;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.support.TestAccessTokens;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamControllerIntegrationTest {

    private static final String PATH = "/api/v1/teams";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestAccessTokens accessTokens;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TeamSettingsRepository teamSettingsRepository;
    @MockitoSpyBean
    private TeamGoalRepository teamGoalRepository;

    @Test
    void createsTeamWithLeaderAndVersionZeroSettingsAndGoals() throws Exception {
        User creator = saveUser();
        String response = createTeam(creator, """
                {"name":"Marketing","description":"Weekly report onboarding"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S201"))
                .andExpect(jsonPath("$.message").value("팀 생성 성공"))
                .andExpect(jsonPath("$.data.teamId").isNumber())
                .andExpect(jsonPath("$.data.name").value("Marketing"))
                .andExpect(jsonPath("$.data.description").value("Weekly report onboarding"))
                .andExpect(jsonPath("$.data.myRole").value("LEADER"))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andExpect(jsonPath("$.timestamp", endsWith("+09:00")))
                .andReturn().getResponse().getContentAsString();
        Long teamId = JsonPath.parse(response).read("$.data.teamId", Long.class);
        entityManager.flush();
        entityManager.clear();

        Team team = teamRepository.findById(teamId).orElseThrow();
        assertThat(team.getCreatedBy().getId()).isEqualTo(creator.getId());
        assertThat(team.getDescription()).isEqualTo("Weekly report onboarding");
        TeamSettings settings = teamSettingsRepository.findByTeamId(teamId).orElseThrow();
        assertThat(settings.getVersion()).isZero();
        assertThat(settings.getTaskType()).isNull();
        assertThat(settings.getMinDailyConversations()).isEqualTo(3);
        assertThat(settings.getHourlyLaborCost()).isEqualTo(30000);
        assertThat(settings.getTargetFrom()).isNull();
        assertThat(settings.getTargetTo()).isNull();
        assertThat(settings.getOnboardingStartDate()).isNull();
        assertThat(settings.getTargetCount()).isNull();
        TeamGoal goals = teamGoalRepository.findByTeamId(teamId).orElseThrow();
        assertThat(goals.getVersion()).isZero();
        assertThat(goals.getSuccessRateMin()).isNull();
        assertThat(goals.getErrorReworkRateMax()).isNull();
        assertThat(goals.getTokensPerSuccessMax()).isNull();

        listTeams(creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].teamId").value(teamId))
                .andExpect(jsonPath("$.data.items[0].name").value("Marketing"))
                .andExpect(jsonPath("$.data.items[0].description").value("Weekly report onboarding"))
                .andExpect(jsonPath("$.data.items[0].myRole").value("LEADER"))
                .andExpect(jsonPath("$.data.items[0].memberCount").value(1));
    }

    @Test
    void paginatesOnlyMembershipsWithTheirRolesPermissionsAndFullMemberCounts() throws Exception {
        User requester = saveUser();
        User otherLeader = saveUser();
        User teammate = saveUser();
        Team ledTeam = saveTeam("Led team", requester);
        Team joinedTeam = saveTeam("Joined team", otherLeader);
        saveTeam("Unrelated team", otherLeader);
        teamMemberRepository.save(TeamMember.member(ledTeam, teammate));
        teamMemberRepository.save(TeamMember.member(joinedTeam, requester));
        teamMemberRepository.save(TeamMember.member(joinedTeam, teammate));
        entityManager.flush();
        jdbc.update("update teams set created_at = ? where id in (?, ?)",
                LocalDateTime.of(2026, 11, 22, 10, 0), ledTeam.getId(), joinedTeam.getId());
        entityManager.clear();

        mockMvc.perform(get(PATH).param("page", "1").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].teamId").value(joinedTeam.getId()))
                .andExpect(jsonPath("$.data.items[0].myRole").value("MEMBER"))
                .andExpect(jsonPath("$.data.items[0].memberCount").value(3))
                .andExpect(jsonPath("$.data.items[0].capabilities").value(is(Map.of(
                        "canViewOwnData", true, "canViewTeamMetrics", false, "canEditSettings", false,
                        "canEditGoals", false, "canManageMembers", false, "canManageSharedCases", false))));

        mockMvc.perform(get(PATH).param("page", "2").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].teamId").value(ledTeam.getId()))
                .andExpect(jsonPath("$.data.items[0].description").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].myRole").value("LEADER"))
                .andExpect(jsonPath("$.data.items[0].memberCount").value(2))
                .andExpect(jsonPath("$.data.items[0].capabilities").value(is(Map.of(
                        "canViewOwnData", true, "canViewTeamMetrics", true, "canEditSettings", true,
                        "canEditGoals", true, "canManageMembers", true, "canManageSharedCases", true))));

        mockMvc.perform(get(PATH).param("page", "3").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void returnsEmptyPageForUserWithoutMemberships() throws Exception {
        listTeams(saveUser())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void acceptsMaximumPageSizeAndRejectsUnsupportedPagination() throws Exception {
        String authorization = bearer(saveUser());
        mockMvc.perform(get(PATH).param("size", "100").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
        mockMvc.perform(get(PATH).param("size", "101").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(PATH).param("page", "0").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get(PATH).param("page", "2147483647").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void acceptsMaximumLengthsAndAnOmittedDescription() throws Exception {
        User creator = saveUser();
        createTeam(creator, "{\"name\":\"%s\",\"description\":\"%s\"}"
                .formatted("a".repeat(100), "b".repeat(500)))
                .andExpect(status().isCreated());
        createTeam(creator, "{\"name\":\"No description\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.description").value(nullValue()));
    }

    @ParameterizedTest
    @MethodSource("invalidTeamRequests")
    void rejectsInvalidTeamInputs(String body) throws Exception {
        createTeam(saveUser(), body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").isString());
    }

    private static Stream<String> invalidTeamRequests() {
        return Stream.of(
                "{}",
                "{\"name\":\"   \"}",
                "{\"name\":\"%s\"}".formatted("a".repeat(101)),
                "{\"name\":\"Team\",\"description\":\"%s\"}".formatted("b".repeat(501)));
    }

    @Test
    void requiresAnAccessTokenForBothEndpoints() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Team\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackTeamMembershipAndSettingsWhenGoalSaveFails() throws Exception {
        User creator = saveUser();
        try {
            doThrow(new DataIntegrityViolationException("Simulated goal insert failure"))
                    .when(teamGoalRepository).save(any(TeamGoal.class));

            createTeam(creator, "{\"name\":\"Rollback check\"}")
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

            ArgumentCaptor<TeamGoal> goal = ArgumentCaptor.forClass(TeamGoal.class);
            verify(teamGoalRepository).save(goal.capture());
            Long teamId = goal.getValue().getTeam().getId();
            assertThat(teamRepository.findById(teamId)).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from team_members where user_id = ?",
                    Long.class, creator.getId())).isZero();
            assertThat(teamSettingsRepository.findByTeamId(teamId)).isEmpty();
            assertThat(teamGoalRepository.findByTeamId(teamId)).isEmpty();
        } finally {
            userRepository.deleteById(creator.getId());
        }
    }

    @Test
    void exposesBothOperationsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/teams'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/teams'].post.responses['201']").exists());
    }

    private User saveUser() {
        return userRepository.save(User.builder()
                .email("team-it-" + UUID.randomUUID() + "@test.com")
                .name("Team test user")
                .passwordHash("test-password-hash")
                .build());
    }

    private Team saveTeam(String name, User leader) {
        Team team = teamRepository.save(Team.builder().name(name).createdBy(leader).build());
        teamMemberRepository.save(TeamMember.leader(team, leader));
        return team;
    }

    private ResultActions createTeam(User user, String body) throws Exception {
        return mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions listTeams(User user) throws Exception {
        return mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private String bearer(User user) {
        return "Bearer " + accessTokens.create(user.getId());
    }
}
