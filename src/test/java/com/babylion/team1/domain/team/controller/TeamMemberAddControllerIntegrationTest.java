package com.babylion.team1.domain.team.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamRole;
import com.babylion.team1.domain.team.repository.TeamMemberRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.domain.user.repository.UserRepository;
import com.babylion.team1.support.TestAccessTokens;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamMemberAddControllerIntegrationTest {

    private static final String PATH = "/api/v1/teams/{teamId}/members";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestAccessTokens accessTokens;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;

    @Test
    void leaderAddsRegisteredUserAsMemberAndTeamRolesRemainIndependent() throws Exception {
        User leader = saveUser("Team leader");
        User invitee = saveUser("Invited user");
        Team inviteeTeam = saveTeam("Invitee's team", invitee);
        Team destination = saveTeam("Destination team", leader);

        addMember(destination, leader, invitee.getEmail())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S201"))
                .andExpect(jsonPath("$.message").value("팀원 추가 성공"))
                .andExpect(jsonPath("$.data.userId").value(invitee.getId()))
                .andExpect(jsonPath("$.data.name").value(invitee.getName()))
                .andExpect(jsonPath("$.data.email").value(invitee.getEmail()))
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andExpect(jsonPath("$.timestamp", endsWith("+09:00")));

        assertThat(teamMemberRepository.findByTeamIdAndUserId(destination.getId(), invitee.getId()))
                .hasValueSatisfying(member -> assertThat(member.getRole()).isEqualTo(TeamRole.MEMBER));
        assertThat(teamMemberRepository.findByTeamIdAndUserId(inviteeTeam.getId(), invitee.getId()))
                .hasValueSatisfying(member -> assertThat(member.getRole()).isEqualTo(TeamRole.LEADER));
        assertThat(teamMemberRepository.countByTeamId(destination.getId())).isEqualTo(2);
    }

    @Test
    void permissionsDependOnTheRoleInTheDestinationTeam() throws Exception {
        User requester = saveUser("Other team leader");
        saveTeam("Requester team", requester);
        Team destination = saveTeam("Destination team", saveUser("Destination leader"));
        User invitee = saveUser("Invited user");
        String unregisteredEmail = UUID.randomUUID() + "@example.com";

        addMember(destination, requester, invitee.getEmail())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        addMember(destination, requester, unregisteredEmail)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        teamMemberRepository.save(TeamMember.member(destination, requester));
        addMember(destination, requester, invitee.getEmail())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_FORBIDDEN"));
        addMember(destination, requester, unregisteredEmail)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_FORBIDDEN"));

        assertThat(teamMemberRepository.findByTeamIdAndUserId(destination.getId(), invitee.getId())).isEmpty();
    }

    @Test
    void rejectsUnknownTeam() throws Exception {
        User requester = saveUser("Requester");

        mockMvc.perform(request(Long.MAX_VALUE, emailBody(requester.getEmail()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(requester)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rejectsUnregisteredEmail() throws Exception {
        User leader = saveUser("Leader");
        Team team = saveTeam("Leader team", leader);

        addMember(team, leader, UUID.randomUUID() + "@example.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("가입되지 않은 이메일입니다."));
    }

    @Test
    void rejectsAnExistingMembership() throws Exception {
        User leader = saveUser("Leader");
        Team team = saveTeam("Leader team", leader);
        User invitee = saveUser("Invited user");
        teamMemberRepository.save(TeamMember.member(team, invitee));

        addMember(team, leader, invitee.getEmail())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
    }

    @Test
    void rejectsAddingTheTeamLeaderAgain() throws Exception {
        User leader = saveUser("Leader");
        Team team = saveTeam("Leader team", leader);

        addMember(team, leader, leader.getEmail())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":\"   \"}", "{\"email\":\"invalid-email\"}"})
    void validatesRequiredEmail(String body) throws Exception {
        User leader = saveUser("Leader");
        Team team = saveTeam("Leader team", leader);

        mockMvc.perform(request(team.getId(), body).header(HttpHeaders.AUTHORIZATION, bearer(leader)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void requiresAValidAccessToken() throws Exception {
        String body = emailBody("member@example.com");
        mockMvc.perform(request(1L, body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(request(1L, body).header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void exposesCreatedResponseInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/members'].post.responses['201']").exists());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRequestsCreateOneMembershipAndReturnOneConflict() throws Exception {
        ConcurrentFixture fixture = transactions.execute(ignored -> {
            User leader = saveUser("Concurrent team leader");
            User invitee = saveUser("Concurrent invitee");
            return new ConcurrentFixture(saveTeam("Concurrent invite", leader), leader, invitee);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier start = new CyclicBarrier(2);
            Callable<AddResult> add = () -> {
                start.await(5, TimeUnit.SECONDS);
                MvcResult result = addMember(fixture.team(), fixture.leader(), fixture.invitee().getEmail())
                        .andReturn();
                String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                return new AddResult(result.getResponse().getStatus(), JsonPath.read(body, "$.code"));
            };
            List<Future<AddResult>> results = executor.invokeAll(List.of(add, add), 20, TimeUnit.SECONDS);

            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(new AddResult(201, "S201"), new AddResult(409, "ALREADY_MEMBER"));
            assertThat(jdbc.queryForObject(
                    "select count(*) from team_members where team_id = ? and user_id = ?",
                    Long.class, fixture.team().getId(), fixture.invitee().getId())).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            transactions.executeWithoutResult(ignored -> {
                jdbc.update("delete from team_members where team_id = ?", fixture.team().getId());
                teamRepository.deleteById(fixture.team().getId());
                userRepository.deleteAllById(List.of(fixture.leader().getId(), fixture.invitee().getId()));
            });
        }
    }

    private User saveUser(String name) {
        return userRepository.save(User.builder()
                .email("team-member-it-" + UUID.randomUUID() + "@example.com")
                .name(name)
                .passwordHash("test-password-hash")
                .build());
    }

    private Team saveTeam(String name, User leader) {
        Team team = teamRepository.save(Team.builder().name(name).createdBy(leader).build());
        teamMemberRepository.save(TeamMember.leader(team, leader));
        return team;
    }

    private ResultActions addMember(Team team, User requester, String email) throws Exception {
        return mockMvc.perform(request(team.getId(), emailBody(email))
                .header(HttpHeaders.AUTHORIZATION, bearer(requester)));
    }

    private MockHttpServletRequestBuilder request(Long teamId, String body) {
        return post(PATH, teamId).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String emailBody(String email) {
        return "{\"email\":\"%s\"}".formatted(email);
    }

    private String bearer(User user) {
        return "Bearer " + accessTokens.create(user.getId());
    }

    private record ConcurrentFixture(Team team, User leader, User invitee) {}

    private record AddResult(int status, String code) {}
}
