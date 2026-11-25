package com.babylion.team1.domain.dashboard.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.dashboard.DashboardFixture;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기준 버전 1: 지난주 월요일 2주 전 수요일 ~ 그다음 주 화요일, 버전 2: 지난주 화요일 ~ 이번 주 이후.
 * 이전 기준에서 쓰던 업무 유형은 비활성이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(DashboardFixture.class)
class FilterOptionsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private DashboardFixture fixture;

    private User leader;
    private User member;
    private User outsider;
    private Team team;
    private TeamTaskType taskType;
    private LocalDate thisMonday;

    @BeforeEach
    void setUp() {
        // 테스트도 로컬 DB를 쓰므로, 직접 가입해 둔 계정과 겹치지 않게 실행마다 고유한 이메일을 쓴다
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        leader = fixture.user("팀장", "filter-it-leader-" + suffix + "@test.com");
        member = fixture.user("팀원", "filter-it-member-" + suffix + "@test.com");
        outsider = fixture.user("외부인", "filter-it-outsider-" + suffix + "@test.com");
        team = fixture.team("기획팀", leader);
        em.persist(TeamMember.leader(team, leader));
        em.persist(TeamMember.member(team, member));

        thisMonday = LocalDate.now(ZoneId.of("Asia/Seoul")).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        TeamTaskType oldTaskType = fixture.taskType(team, "이전 업무 유형");
        oldTaskType.deactivate();
        taskType = fixture.taskType(team, "주간 보고서 작성");
        LocalDate firstMonday = thisMonday.minusWeeks(3);
        TeamSettings first = fixture.settings(team, oldTaskType, firstMonday.plusDays(2), firstMonday.plusDays(8));
        fixture.nextSettings(first, taskType, thisMonday.minusDays(6), thisMonday.plusWeeks(2));

        Team otherTeam = fixture.team("다른 팀", outsider);
        em.persist(TeamMember.leader(otherTeam, outsider));
        fixture.settings(otherTeam, fixture.taskType(otherTeam, "다른 팀 업무"), thisMonday.minusWeeks(8), thisMonday);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("ME: 일·주 단위를 고를 수 있고 availableWeeks는 빈 목록이다. 팀장도 조회할 수 있다")
    void personalOptions() throws Exception {
        for (User user : new User[] {member, leader}) {
            perform(user, "?scope=ME")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("S200"))
                    .andExpect(jsonPath("$.message").value("조회 성공"))
                    .andExpect(jsonPath("$.data.length()").value(8))
                    .andExpect(jsonPath("$.data.scope").value("ME"))
                    .andExpect(jsonPath("$.data.taskTypes", hasSize(1)))
                    .andExpect(jsonPath("$.data.taskTypes[0].length()").value(2))
                    .andExpect(jsonPath("$.data.taskTypes[0].taskTypeId").value(taskType.getId()))
                    .andExpect(jsonPath("$.data.taskTypes[0].name").value("주간 보고서 작성"))
                    .andExpect(jsonPath("$.data.settingsVersions", contains(1, 2)))
                    .andExpect(jsonPath("$.data.allowedUnits", contains("DAY", "WEEK")))
                    .andExpect(jsonPath("$.data.availableWeeks", hasSize(0)))
                    .andExpect(jsonPath("$.data.allowedModels", hasSize(0)))
                    .andExpect(jsonPath("$.data.policyVersion").value(1))
                    .andExpect(jsonPath("$.data.comparisonAvailable").value(false));
        }
    }

    @Test
    @DisplayName("TEAM: 주 단위만 고를 수 있고, 대상 기간에 걸친 완료된 주만 응답한다(이번 주 제외)")
    void teamOptions() throws Exception {
        perform(leader, "?scope=TEAM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("TEAM"))
                .andExpect(jsonPath("$.data.settingsVersions", contains(1, 2)))
                .andExpect(jsonPath("$.data.allowedUnits", contains("WEEK")))
                .andExpect(jsonPath("$.data.availableWeeks[*].weekStart", contains(
                        thisMonday.minusWeeks(3).toString(),
                        thisMonday.minusWeeks(2).toString(),
                        thisMonday.minusWeeks(1).toString())))
                .andExpect(jsonPath("$.data.availableWeeks[2].weekEnd").value(thisMonday.minusDays(1).toString()))
                .andExpect(jsonPath("$.data.allowedModels", hasSize(0)))
                .andExpect(jsonPath("$.data.comparisonAvailable").value(false));
    }

    @Test
    @DisplayName("기준을 저장한 적이 없는 팀은 빈 목록을 응답한다")
    void unconfiguredTeam() throws Exception {
        Team newTeam = fixture.team("새 팀", leader);
        em.persist(TeamMember.leader(newTeam, leader));
        em.persist(TeamSettings.createDefault(newTeam));
        em.flush();

        mockMvc.perform(fixture.get(leader, url(newTeam.getId()) + "?scope=TEAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskTypes", hasSize(0)))
                .andExpect(jsonPath("$.data.settingsVersions", hasSize(0)))
                .andExpect(jsonPath("$.data.availableWeeks", hasSize(0)));
    }

    @Test
    @DisplayName("팀원이 TEAM을 요청하면 403, 다른 팀이거나 없는 팀이면 404다")
    void accessDenied() throws Exception {
        perform(member, "?scope=TEAM")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_FORBIDDEN"));
        perform(outsider, "?scope=ME")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(fixture.get(leader, url(Long.MAX_VALUE) + "?scope=ME"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?scope=", "?scope=ALL", "?scope=ME&models=gpt-4o", "?scope=TEAM&weekStart=2026-11-02"})
    @DisplayName("scope가 없거나 잘못됐거나 지원하지 않는 파라미터가 있으면 422 VALIDATION_FAILED다")
    void invalidRequest(String query) throws Exception {
        perform(leader, query)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("v3 경로는 더 이상 제공하지 않는다")
    void oldPathIsRemoved() throws Exception {
        mockMvc.perform(fixture.get(leader, "/api/v1/teams/" + team.getId() + "/filter-options?scope=ME"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401 AUTH_REQUIRED로 응답한다")
    void withoutToken() throws Exception {
        mockMvc.perform(get(url(team.getId()) + "?scope=ME"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    private ResultActions perform(User user, String query) throws Exception {
        return mockMvc.perform(fixture.get(user, url(team.getId()) + query));
    }

    private String url(Long teamId) {
        return "/api/v1/teams/" + teamId + "/dashboard/filter-options";
    }
}
