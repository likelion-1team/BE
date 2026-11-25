package com.babylion.team1.domain.dashboard.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.analysis.entity.AnalysisOutcome;
import com.babylion.team1.domain.analysis.entity.AnalysisReworkType;
import com.babylion.team1.domain.dashboard.DashboardFixture;
import com.babylion.team1.domain.dashboard.dto.MetricCode;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 팀장 1명 + 팀원 5명, 기준 버전 1 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(DashboardFixture.class)
class TrendControllerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final String ME =
            "?scope=ME&from=2026-11-02&to=2026-11-15&settingsVersion=1&metric=confirmedSuccessRate&unit=DAY";
    private static final String TEAM =
            "?scope=TEAM&weekStart=2026-11-02&settingsVersion=1&metric=confirmedSuccessRate&unit=WEEK";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private DashboardFixture fixture;

    private User leader;
    private final List<User> members = new ArrayList<>();
    private User member;
    private User outsider;
    private Team team;
    private TeamSettings settings;

    @BeforeEach
    void setUp() {
        // 테스트도 로컬 DB를 쓰므로, 직접 가입해 둔 계정과 겹치지 않게 실행마다 고유한 이메일을 쓴다
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        leader = fixture.user("팀장", "trend-it-leader-" + suffix + "@test.com");
        outsider = fixture.user("외부인", "trend-it-outsider-" + suffix + "@test.com");
        team = fixture.team("기획팀", leader);
        em.persist(TeamMember.leader(team, leader));
        for (int index = 0; index < 5; index++) {
            User user = fixture.user("팀원" + index, "trend-it-member" + index + "-" + suffix + "@test.com");
            em.persist(TeamMember.member(team, user));
            members.add(user);
        }
        member = members.get(0);
        settings = fixture.settings(team, fixture.taskType(team, "주간 보고서 작성"), MONDAY, MONDAY.plusDays(18));
    }

    @Test
    @DisplayName("ME·DAY: 모든 날을 오름차순으로 응답하고 상태는 날마다 판정하며 대화 없는 날은 NO_DATA다")
    void dailyTrend() throws Exception {
        saveItems(20, member, MONDAY, AnalysisOutcome.SUCCESS);
        saveItems(19, member, MONDAY.plusDays(2), AnalysisOutcome.FAILURE);
        saveItems(1, members.get(1), MONDAY, AnalysisOutcome.FAILURE);

        perform(member, ME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data.scope").value("ME"))
                .andExpect(jsonPath("$.data.asOf", endsWith("+09:00")))
                .andExpect(jsonPath("$.data.metricDefinitionVersion").value(1))
                .andExpect(jsonPath("$.data.onboardingStartDate").doesNotExist())
                .andExpect(jsonPath("$.data.points", hasSize(14)))
                .andExpect(jsonPath("$.data.points[0].periodStart").value("2026-11-02"))
                .andExpect(jsonPath("$.data.points[0].metric.code").value("confirmedSuccessRate"))
                .andExpect(jsonPath("$.data.points[0].metric.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.points[0].metric.value").value(1.0))
                .andExpect(jsonPath("$.data.points[1].periodStart").value("2026-11-03"))
                .andExpect(jsonPath("$.data.points[1].metric.status").value("NO_DATA"))
                .andExpect(jsonPath("$.data.points[1].metric.value").value(nullValue()))
                .andExpect(jsonPath("$.data.points[2].metric.status").value("INSUFFICIENT_SAMPLE"))
                .andExpect(jsonPath("$.data.points[2].metric.value").value(nullValue()))
                .andExpect(jsonPath("$.data.points[2].metric.numerator").value(0))
                .andExpect(jsonPath("$.data.points[2].metric.denominator").value(19))
                .andExpect(jsonPath("$.data.points[13].periodStart").value("2026-11-15"));
    }

    @Test
    @DisplayName("ME·WEEK: 월요일로 묶고 양 끝 주는 조회 기간 안의 대화만 세며 빈 주도 포함한다")
    void weeklyTrend() throws Exception {
        saveItems(20, member, MONDAY, AnalysisOutcome.SUCCESS);
        saveItems(10, member, MONDAY.plusDays(2), AnalysisOutcome.SUCCESS);
        saveItems(10, member, MONDAY.plusDays(6), AnalysisOutcome.FAILURE);

        // 11-04(수) ~ 11-17(화)
        perform(member, ME.replace("unit=DAY", "unit=WEEK").replace("from=2026-11-02", "from=2026-11-04")
                .replace("to=2026-11-15", "to=2026-11-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points[*].periodStart",
                        contains("2026-11-02", "2026-11-09", "2026-11-16")))
                .andExpect(jsonPath("$.data.points[0].metric.denominator").value(20))
                .andExpect(jsonPath("$.data.points[0].metric.value").value(0.5))
                .andExpect(jsonPath("$.data.points[1].metric.status").value("NO_DATA"))
                .andExpect(jsonPath("$.data.points[2].metric.status").value("NO_DATA"));
    }

    @ParameterizedTest
    @EnumSource(MetricCode.class)
    @DisplayName("같은 조건이면 KPI와 추이의 cohortId와 지표가 같다 (ME, 팀장·팀원의 TEAM)")
    void matchesKpi(MetricCode code) throws Exception {
        for (User user : members) {
            saveItems(4, user, MONDAY, AnalysisOutcome.SUCCESS);
            saveItems(1, user, MONDAY.plusDays(6), AnalysisOutcome.FAILURE);
        }
        saveItems(1, leader, MONDAY, null);

        assertMatchesKpi(member, "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1", code);
        for (User viewer : List.of(leader, member)) {
            assertMatchesKpi(viewer, "?scope=TEAM&weekStart=2026-11-02&settingsVersion=1", code);
        }
    }

    @Test
    @DisplayName("TEAM: 고른 주 하나만 응답하고, 기여자가 부족하면 값과 건수를 숨긴다(팀장·팀원 동일)")
    void teamWeek() throws Exception {
        for (User user : members.subList(0, 4)) {
            saveItems(6, user, MONDAY, AnalysisOutcome.SUCCESS);
            saveItems(6, user, MONDAY.plusWeeks(1), AnalysisOutcome.SUCCESS);
        }
        for (User viewer : List.of(leader, member)) {
            perform(viewer, TEAM)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.points", hasSize(1)))
                    .andExpect(jsonPath("$.data.points[0].periodStart").value("2026-11-02"))
                    .andExpect(jsonPath("$.data.points[0].metric.status").value("SUPPRESSED"))
                    .andExpect(jsonPath("$.data.points[0].metric.value").value(nullValue()))
                    .andExpect(jsonPath("$.data.points[0].metric.numerator").value(nullValue()))
                    .andExpect(jsonPath("$.data.points[0].metric.denominator").value(nullValue()))
                    .andExpect(jsonPath("$.data.points[0].metric.sampleSize").value(nullValue()))
                    .andExpect(jsonPath("$.data.points[0].metric.missingCount").value(nullValue()));
        }

        saveItems(1, members.get(4), MONDAY, AnalysisOutcome.FAILURE);
        for (User viewer : List.of(leader, member)) {
            perform(viewer, TEAM)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.points", hasSize(1)))
                    .andExpect(jsonPath("$.data.points[0].metric.value").value(0.96));
        }
        assertThat((Object) JsonPath.read(body(perform(member, TEAM)), "$.data.points"))
                .isEqualTo(JsonPath.read(body(perform(leader, TEAM)), "$.data.points"));
    }

    @Test
    @DisplayName("다른 팀이거나 없는 팀이면 404 RESOURCE_NOT_FOUND다")
    void otherTeam() throws Exception {
        perform(outsider, TEAM)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(fixture.get(member, "/api/v1/teams/" + Long.MAX_VALUE + "/dashboard/trends" + TEAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1&unit=DAY",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1&metric=confirmedSuccessRate",
            "?scope=ME&from=2026-11-02&to=2026-11-08&metric=confirmedSuccessRate&unit=DAY",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=99&metric=confirmedSuccessRate&unit=DAY",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1&metric=successRate&unit=DAY",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1&metric=CONFIRMED_SUCCESS_RATE&unit=DAY",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1&metric=confirmedSuccessRate&unit=MONTH",
            "?scope=ME&from=2026-11-08&to=2026-11-02&settingsVersion=1&metric=confirmedSuccessRate&unit=DAY",
            // 367일
            "?scope=ME&from=2025-11-01&to=2026-11-02&settingsVersion=1&metric=confirmedSuccessRate&unit=WEEK",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1&metric=confirmedSuccessRate&unit=DAY&models=o3",
            "?scope=TEAM&weekStart=2026-11-02&settingsVersion=1&metric=confirmedSuccessRate&unit=DAY",
            "?scope=TEAM&weekStart=2026-11-02&settingsVersion=1&metric=confirmedSuccessRate",
            "?scope=TEAM&weekStart=2026-11-04&settingsVersion=1&metric=confirmedSuccessRate&unit=WEEK",
            "?scope=TEAM&from=2026-11-02&to=2026-11-08&settingsVersion=1&metric=confirmedSuccessRate&unit=WEEK"
    })
    @DisplayName("필수 값 누락, 잘못된 값, TEAM의 DAY, 지원하지 않는 파라미터는 422 VALIDATION_FAILED다")
    void invalidRequest(String query) throws Exception {
        perform(leader, query)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("ME는 366일까지 조회할 수 있다")
    void maxPeriod() throws Exception {
        perform(member, ME.replace("from=2026-11-02", "from=2025-11-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points", hasSize(366)));
    }

    @Test
    @DisplayName("TEAM: 진행 중인 주는 422다")
    void weekMustBeCompleted() throws Exception {
        LocalDate thisMonday = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        perform(leader, TEAM.replace("2026-11-02", thisMonday.toString()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0].field").value("weekStart"));
    }

    @Test
    @DisplayName("API 문서에 v4 조회 조건과 metric 값이 노출된다")
    void apiDocsExposeMetric() throws Exception {
        String parameters = "$.paths['/api/v1/teams/{teamId}/dashboard/trends'].get.parameters";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(parameters + "[*].name", containsInAnyOrder("teamId", "scope", "from", "to",
                        "weekStart", "settingsVersion", "metric", "unit")))
                .andExpect(jsonPath(parameters + "[?(@.name == 'metric')].required").value(true))
                .andExpect(jsonPath(parameters + "[?(@.name == 'metric')].schema.enum[0]")
                        .value("confirmedSuccessRate"));
    }

    private void assertMatchesKpi(User user, String query, MetricCode code) throws Exception {
        String kpis = body(mockMvcGet(user, "kpis" + query));
        String trends = body(mockMvcGet(user, "trends" + query + "&unit=WEEK&metric=" + code.getValue()));

        assertThat((Object) JsonPath.read(trends, "$.data.cohortId"))
                .isEqualTo(JsonPath.read(kpis, "$.data.cohortId"));
        assertThat((Object) JsonPath.read(trends, "$.data.points.length()")).isEqualTo(1);
        assertThat((Object) JsonPath.read(trends, "$.data.points[0].metric"))
                .isEqualTo(JsonPath.read(kpis, "$.data.metrics[" + code.ordinal() + "]"));
    }

    private void saveItems(int count, User user, LocalDate date, AnalysisOutcome outcome) {
        for (int index = 0; index < count; index++) {
            fixture.save(fixture.item(settings, user, date).confirmedOutcome(outcome)
                    .confirmedReworkType(AnalysisReworkType.NONE));
        }
    }

    private String body(ResultActions result) throws Exception {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions perform(User user, String query) throws Exception {
        return mockMvcGet(user, "trends" + query);
    }

    private ResultActions mockMvcGet(User user, String path) throws Exception {
        em.flush();
        em.clear();
        return mockMvc.perform(fixture.get(user, "/api/v1/teams/" + team.getId() + "/dashboard/" + path));
    }
}
