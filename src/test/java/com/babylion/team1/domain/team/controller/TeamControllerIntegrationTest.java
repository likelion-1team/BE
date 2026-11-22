package com.babylion.team1.domain.team.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.global.jwt.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
    private JwtProvider jwtProvider;
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
    @MockitoSpyBean
    private TeamSettingsRepository teamSettingsRepository;

    @Test
    void createsTeamWithLeaderAndDefaultSettings() throws Exception {
        User creator = saveUser();
        String response = createTeam(creator, """
                {"name":"Marketing","description":"Weekly report onboarding"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S201"))
                .andExpect(jsonPath("$.message").value("팀 생성 성공"))
                .andExpect(jsonPath("$.data.teamId").isNumber())
                .andReturn().getResponse().getContentAsString();
        Long teamId = JsonPath.parse(response).read("$.data.teamId", Long.class);
        entityManager.flush();
        entityManager.clear();

        Team team = teamRepository.findById(teamId).orElseThrow();
        assertThat(team.getCreatedBy().getId()).isEqualTo(creator.getId());
        assertThat(team.getDescription()).isEqualTo("Weekly report onboarding");
        TeamSettings settings = teamSettingsRepository.findByTeamId(teamId).orElseThrow();
        assertThat(settings.getMinDailyConversations()).isEqualTo(3);
        assertThat(settings.getHourlyLaborCost()).isEqualTo(30000);
        assertThat(settings.getTargetFrom()).isNull();
        assertThat(settings.getTargetTo()).isNull();
        assertThat(settings.getOnboardingStartDate()).isNull();
        assertThat(settings.getTargetCount()).isNull();

        listTeams(creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].teamId").value(teamId))
                .andExpect(jsonPath("$.data[0].name").value("Marketing"))
                .andExpect(jsonPath("$.data[0].myRole").value("LEADER"))
                .andExpect(jsonPath("$.data[0].memberCount").value(1));
    }

    @Test
    void listsOnlyMembershipsWithTheirRolesAndFullMemberCounts() throws Exception {
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

        listTeams(requester)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].teamId").value(joinedTeam.getId()))
                .andExpect(jsonPath("$.data[0].myRole").value("MEMBER"))
                .andExpect(jsonPath("$.data[0].memberCount").value(3))
                .andExpect(jsonPath("$.data[1].teamId").value(ledTeam.getId()))
                .andExpect(jsonPath("$.data[1].myRole").value("LEADER"))
                .andExpect(jsonPath("$.data[1].memberCount").value(2));
    }

    @Test
    void returnsEmptyArrayForUserWithoutMemberships() throws Exception {
        listTeams(saveUser())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void acceptsMaximumLengthsAndAnOmittedDescription() throws Exception {
        User creator = saveUser();
        createTeam(creator, "{\"name\":\"%s\",\"description\":\"%s\"}"
                .formatted("a".repeat(100), "b".repeat(500)))
                .andExpect(status().isCreated());
        createTeam(creator, "{\"name\":\"No description\"}")
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @MethodSource("invalidTeamRequests")
    void rejectsInvalidTeamInputs(String body) throws Exception {
        createTeam(saveUser(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
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
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Team\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackTeamAndMembershipWhenSettingsSaveFails() throws Exception {
        User creator = saveUser();
        try {
            doThrow(new DataIntegrityViolationException("Simulated settings insert failure"))
                    .when(teamSettingsRepository).save(any(TeamSettings.class));

            createTeam(creator, "{\"name\":\"Rollback check\"}")
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

            assertThat(jdbc.queryForObject("select count(*) from teams where created_by = ?",
                    Long.class, creator.getId())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from team_members where user_id = ?",
                    Long.class, creator.getId())).isZero();
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
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }
}
