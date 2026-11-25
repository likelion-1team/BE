package com.babylion.team1.domain.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.analysis.entity.AnalysisItem;
import com.babylion.team1.domain.analysis.entity.AnalysisItemStatus;
import com.babylion.team1.domain.analysis.entity.AnalysisOutcome;
import com.babylion.team1.domain.analysis.entity.AnalysisReworkType;
import com.babylion.team1.domain.analysis.entity.ScopeClassification;
import com.babylion.team1.domain.conversation.entity.ConversationVersion;
import com.babylion.team1.domain.dashboard.DashboardFixture;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamGoal;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 팀장 1명 + 팀원 5명, 기준 버전 1. 주는 2026-11-02(월) ~ 2026-11-08(일)을 쓴다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(DashboardFixture.class)
class DashboardControllerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);
    private static final String ME = "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1";
    private static final String TEAM = "?scope=TEAM&weekStart=2026-11-02&settingsVersion=1";

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
        leader = fixture.user("팀장", "dashboard-it-leader-" + suffix + "@test.com");
        outsider = fixture.user("외부인", "dashboard-it-outsider-" + suffix + "@test.com");
        team = fixture.team("기획팀", leader);
        em.persist(TeamMember.leader(team, leader));
        for (int index = 0; index < 5; index++) {
            User user = fixture.user("팀원" + index, "dashboard-it-member" + index + "-" + suffix + "@test.com");
            em.persist(TeamMember.member(team, user));
            members.add(user);
        }
        member = members.get(0);
        TeamTaskType taskType = fixture.taskType(team, "주간 보고서 작성");
        settings = fixture.settings(team, taskType, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 20));
    }

    @Test
    @DisplayName("ME: 본인 항목만 직원 확인 값으로 집계하고 지표 5개와 coverage를 응답한다")
    void personalMetrics() throws Exception {
        saveItems(20, member, AnalysisOutcome.SUCCESS, AnalysisReworkType.NONE);
        saveItems(5, member, AnalysisOutcome.FAILURE, AnalysisReworkType.ERROR_CORRECTION);
        saveItems(1, members.get(1), AnalysisOutcome.FAILURE, AnalysisReworkType.ERROR_CORRECTION);

        perform(member, ME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$.data.scope").value("ME"))
                .andExpect(jsonPath("$.data.asOf", endsWith("+09:00")))
                .andExpect(jsonPath("$.data.metricDefinitionVersion").value(1))
                .andExpect(jsonPath("$.data.policyVersion").value(1))
                .andExpect(jsonPath("$.data.settingsVersion").value(1))
                .andExpect(jsonPath("$.data.metrics[*].code", contains("confirmedSuccessRate",
                        "outcomeConfirmationRate", "errorReworkRate", "reworkConfirmationRate",
                        "tokensPerConfirmedSuccess")))
                .andExpect(jsonPath("$.data.metrics[*].value", contains(0.8, 1.0, 0.2, 1.0, 1250.0)))
                .andExpect(jsonPath("$.data.metrics[*].status", everyItem(is("AVAILABLE"))))
                .andExpect(jsonPath("$.data.metrics[0].length()").value(11))
                .andExpect(jsonPath("$.data.metrics[0].unit").value("RATIO"))
                .andExpect(jsonPath("$.data.metrics[0].numerator").value(20))
                .andExpect(jsonPath("$.data.metrics[0].denominator").value(25))
                .andExpect(jsonPath("$.data.metrics[0].sampleSize").value(25))
                .andExpect(jsonPath("$.data.metrics[0].missingCount").value(0))
                .andExpect(jsonPath("$.data.metrics[0].assessmentSource").value("SELF_REPORT"))
                .andExpect(jsonPath("$.data.metrics[0].valueSource").value("USER_INPUT"))
                .andExpect(jsonPath("$.data.metrics[0].reasonCodes", hasSize(0)))
                .andExpect(jsonPath("$.data.metrics[4].unit").value("TOKENS_PER_SUCCESS"))
                .andExpect(jsonPath("$.data.metrics[4].numerator").value(25000))
                .andExpect(jsonPath("$.data.metrics[4].denominator").value(20))
                .andExpect(jsonPath("$.data.metrics[4].valueSource").value("TOKENIZER_ESTIMATE"))
                .andExpect(jsonPath("$.data.coverage.selectedCount").value(25))
                .andExpect(jsonPath("$.data.coverage.inScopeCount").value(25));
    }

    @Test
    @DisplayName("분석 전·실패·범위 밖·삭제된 버전·이전 버전·다른 설정 버전·기간 밖은 지표에서 제외하고 coverage에 나눠 센다")
    void excludesIneligibleItems() throws Exception {
        TeamSettings nextSettings = fixture.nextSettings(settings, settings.getTaskType(), MONDAY, SUNDAY);
        fixture.save(confirmed(fixture.item(settings, member, MONDAY)));
        fixture.save(confirmed(fixture.item(settings, member, SUNDAY)));
        fixture.save(confirmed(fixture.item(settings, member, MONDAY).status(AnalysisItemStatus.QUEUED).current(false)
                .scopeClassification(null)));
        fixture.save(confirmed(fixture.item(settings, member, MONDAY).status(AnalysisItemStatus.RUNNING).current(false)
                .scopeClassification(null)));
        fixture.save(confirmed(fixture.item(settings, member, MONDAY).status(AnalysisItemStatus.FAILED).current(false)
                .scopeClassification(null)));
        fixture.save(confirmed(fixture.item(settings, member, MONDAY)
                .scopeClassification(ScopeClassification.OUT_OF_SCOPE)));
        fixture.save(confirmed(fixture.item(settings, member, MONDAY)
                .scopeClassification(ScopeClassification.MIXED_TASKS)));
        // 새 버전으로 교체된 이전 버전
        fixture.save(confirmed(fixture.item(settings, member, MONDAY).current(false)));
        // 삭제된 버전
        ConversationVersion source = fixture.version(settings, member, MONDAY);
        ConversationVersion deleted = fixture.version(source.getConversation(), 2, Instant.now());
        fixture.save(confirmed(fixture.item(settings, deleted)));
        fixture.save(confirmed(fixture.item(nextSettings, member, MONDAY)));
        fixture.save(confirmed(fixture.item(settings, member, MONDAY.minusDays(1))));
        fixture.save(confirmed(fixture.item(settings, member, SUNDAY.plusDays(1))));

        perform(member, ME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics[*].denominator", contains(2, 2, 2, 2, 2)))
                .andExpect(jsonPath("$.data.metrics[4].numerator").value(2000))
                .andExpect(jsonPath("$.data.coverage.selectedCount").value(7))
                .andExpect(jsonPath("$.data.coverage.inScopeCount").value(2))
                .andExpect(jsonPath("$.data.coverage.excludedCount").value(2))
                .andExpect(jsonPath("$.data.coverage.pendingCount").value(2))
                .andExpect(jsonPath("$.data.coverage.failedCount").value(1))
                .andExpect(jsonPath("$.data.coverage.outcomeConfirmedCount").value(2))
                .andExpect(jsonPath("$.data.coverage.reworkConfirmedCount").value(2))
                .andExpect(jsonPath("$.data.coverage.tokenMeasuredCount").value(2));

        perform(member, ME.replace("settingsVersion=1", "settingsVersion=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settingsVersion").value(2))
                .andExpect(jsonPath("$.data.coverage.selectedCount").value(1));
    }

    @Test
    @DisplayName("AI 판정만 있거나 UNKNOWN으로 확인한 항목은 확인 수에서 제외한다")
    void excludesAiJudgementAndUnknown() throws Exception {
        saveItems(2, member, null, null);
        saveItems(3, member, AnalysisOutcome.UNKNOWN, AnalysisReworkType.UNKNOWN);
        saveItems(1, member, AnalysisOutcome.SUCCESS, AnalysisReworkType.NONE);

        perform(member, ME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics[*].numerator", contains(1, 1, 0, 1, 6000)))
                .andExpect(jsonPath("$.data.metrics[*].denominator", contains(1, 6, 1, 6, 1)))
                .andExpect(jsonPath("$.data.metrics[*].missingCount", contains(5, 5, 5, 5, 0)))
                .andExpect(jsonPath("$.data.metrics[*].status", everyItem(is("INSUFFICIENT_SAMPLE"))))
                .andExpect(jsonPath("$.data.metrics[*].value", everyItem(nullValue())))
                .andExpect(jsonPath("$.data.coverage.outcomeConfirmedCount").value(1))
                .andExpect(jsonPath("$.data.coverage.reworkConfirmedCount").value(1));
    }

    @Test
    @DisplayName("같은 버전을 다시 분석했으면 coverage에는 최신 항목만 센다")
    void coverageCountsLatestItemOfVersion() throws Exception {
        ConversationVersion version = fixture.version(settings, member, MONDAY);
        fixture.save(fixture.item(settings, version).status(AnalysisItemStatus.FAILED).current(false)
                .scopeClassification(null).estimatedTokens(null));
        fixture.save(fixture.item(settings, version).estimatedTokens(null));

        perform(member, ME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverage.selectedCount").value(1))
                .andExpect(jsonPath("$.data.coverage.inScopeCount").value(1))
                .andExpect(jsonPath("$.data.coverage.failedCount").value(0))
                .andExpect(jsonPath("$.data.coverage.tokenMeasuredCount").value(0))
                .andExpect(jsonPath("$.data.metrics[4].status").value("NOT_MEASURED"));
    }

    @Test
    @DisplayName("데이터가 없으면 ME는 NO_DATA와 0건 coverage를 응답한다")
    void noData() throws Exception {
        perform(member, ME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics[*].status", everyItem(is("NO_DATA"))))
                .andExpect(jsonPath("$.data.coverage.selectedCount").value(0));
    }

    @Test
    @DisplayName("TEAM: 기여자 4명이면 200으로 응답하되 모든 값·건수와 coverage를 숨기고, 5명이면 제공한다(팀장·팀원 동일)")
    void teamSuppressionBoundary() throws Exception {
        for (User user : members.subList(0, 4)) {
            saveItems(6, user, AnalysisOutcome.SUCCESS, AnalysisReworkType.NONE);
        }
        for (User viewer : List.of(leader, member)) {
            perform(viewer, TEAM)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.scope").value("TEAM"))
                    .andExpect(jsonPath("$.data.coverage").value(nullValue()))
                    .andExpect(jsonPath("$.data.metrics[*].status", contains("SUPPRESSED", "SUPPRESSED", "SUPPRESSED",
                            "SUPPRESSED", "SUPPRESSED")))
                    .andExpect(jsonPath("$.data.metrics[*].value", everyItem(nullValue())))
                    .andExpect(jsonPath("$.data.metrics[*].numerator", everyItem(nullValue())))
                    .andExpect(jsonPath("$.data.metrics[*].denominator", everyItem(nullValue())))
                    .andExpect(jsonPath("$.data.metrics[*].sampleSize", everyItem(nullValue())))
                    .andExpect(jsonPath("$.data.metrics[*].missingCount", everyItem(nullValue())));
        }

        saveItems(1, members.get(4), AnalysisOutcome.FAILURE, AnalysisReworkType.NONE);
        for (User viewer : List.of(leader, member)) {
            perform(viewer, TEAM)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.metrics[*].status", everyItem(is("AVAILABLE"))))
                    .andExpect(jsonPath("$.data.metrics[0].value").value(0.96))
                    .andExpect(jsonPath("$.data.coverage.inScopeCount").value(25));
        }
    }

    @Test
    @DisplayName("TEAM: 지표 일부만 숨겨도 coverage는 null이다(팀장·팀원 동일)")
    void coverageIsNullWhenAnyMetricIsSuppressed() throws Exception {
        for (User user : members.subList(0, 4)) {
            saveItems(6, user, AnalysisOutcome.SUCCESS, AnalysisReworkType.NONE);
        }
        saveItems(1, members.get(4), AnalysisOutcome.SUCCESS, null);

        for (User viewer : List.of(leader, member)) {
            perform(viewer, TEAM)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.metrics[*].status", contains("AVAILABLE", "AVAILABLE", "SUPPRESSED",
                            "SUPPRESSED", "AVAILABLE")))
                    .andExpect(jsonPath("$.data.coverage").value(nullValue()));
        }
    }

    @Test
    @DisplayName("TEAM: 월요일부터 일요일까지 포함하고 다른 주의 항목은 제외한다")
    void teamWeekRange() throws Exception {
        for (User user : members) {
            saveItems(4, user, AnalysisOutcome.SUCCESS, AnalysisReworkType.NONE);
            fixture.save(confirmed(fixture.item(settings, user, SUNDAY)));
            fixture.save(confirmed(fixture.item(settings, user, SUNDAY.plusDays(1))));
            fixture.save(confirmed(fixture.item(settings, user, MONDAY.minusDays(1))));
        }

        perform(leader, TEAM)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics[0].denominator").value(25))
                .andExpect(jsonPath("$.data.coverage.selectedCount").value(25));
    }

    @Test
    @DisplayName("goals는 팀 목표의 현재 값이고, 미설정이면 version 0에 값은 null이다")
    void goals() throws Exception {
        perform(member, ME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals.length()").value(4))
                .andExpect(jsonPath("$.data.goals.version").value(0))
                .andExpect(jsonPath("$.data.goals.successRateMin").value(nullValue()))
                .andExpect(jsonPath("$.data.goals.errorReworkRateMax").value(nullValue()))
                .andExpect(jsonPath("$.data.goals.tokensPerSuccessMax").value(nullValue()));

        TeamGoal goal = TeamGoal.createDefault(team);
        ReflectionTestUtils.setField(goal, "successRateMin", new BigDecimal("0.7"));
        ReflectionTestUtils.setField(goal, "tokensPerSuccessMax", 6000);
        em.persist(goal);
        em.flush();

        perform(member, ME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals.version").value(goal.getVersion()))
                .andExpect(jsonPath("$.data.goals.successRateMin").value(0.7))
                .andExpect(jsonPath("$.data.goals.errorReworkRateMax").value(nullValue()))
                .andExpect(jsonPath("$.data.goals.tokensPerSuccessMax").value(6000));
    }

    @Test
    @DisplayName("cohortId는 같은 조건이면 같고 사용자·기간·범위·설정 버전이 다르면 다르다")
    void cohortId() throws Exception {
        fixture.nextSettings(settings, settings.getTaskType(), MONDAY, SUNDAY);
        String base = cohortIdOf(leader, ME);

        expectCohortId(leader, ME, is(base));
        expectCohortId(member, ME, not(base));
        expectCohortId(leader, TEAM, not(base));
        expectCohortId(leader, ME.replace("to=2026-11-08", "to=2026-11-09"), not(base));
        expectCohortId(leader, ME.replace("settingsVersion=1", "settingsVersion=2"), not(base));
    }

    @Test
    @DisplayName("팀장과 팀원 모두 ME와 TEAM을 조회할 수 있고, 같은 조건이면 팀 지표가 같다")
    void roles() throws Exception {
        for (User user : members) {
            saveItems(4, user, AnalysisOutcome.SUCCESS, AnalysisReworkType.NONE);
        }
        saveItems(5, leader, AnalysisOutcome.FAILURE, AnalysisReworkType.ERROR_CORRECTION);

        for (User viewer : List.of(leader, member)) {
            perform(viewer, ME).andExpect(status().isOk()).andExpect(jsonPath("$.data.scope").value("ME"));
        }
        perform(member, TEAM)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("TEAM"))
                .andExpect(jsonPath("$.data.metrics[0].value").value(0.8));
        assertThat(teamData(member)).isEqualTo(teamData(leader));
    }

    @Test
    @DisplayName("다른 팀이거나 없는 팀이면 요청 값과 상관없이 404 RESOURCE_NOT_FOUND다")
    void otherTeam() throws Exception {
        for (String query : List.of(ME, TEAM, "")) {
            perform(outsider, query)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        for (String query : List.of(ME, TEAM)) {
            mockMvc.perform(fixture.get(member, "/api/v1/teams/" + Long.MAX_VALUE + "/dashboard/kpis" + query))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "?scope=ALL&from=2026-11-02&to=2026-11-08&settingsVersion=1",
            "?scope=me&from=2026-11-02&to=2026-11-08&settingsVersion=1",
            "?scope=ME&scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1",
            "?scope=ME&to=2026-11-08&settingsVersion=1",
            "?scope=ME&from=2026-11-02&settingsVersion=1",
            "?scope=ME&from=2026-11-02&to=2026-11-08",
            "?scope=ME&from=&to=2026-11-08&settingsVersion=1",
            "?scope=ME&from=2026/11/02&to=2026-11-08&settingsVersion=1",
            "?scope=ME&from=2026-11-31&to=2026-12-08&settingsVersion=1",
            "?scope=ME&from=2026-11-09&to=2026-11-08&settingsVersion=1",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=abc",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=99",
            // version 0은 팀 생성 시 만든 미설정 상태라 고를 수 없다
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=0",
            "?scope=ME&weekStart=2026-11-02&settingsVersion=1",
            "?scope=ME&from=2026-11-02&to=2026-11-08&weekStart=2026-11-02&settingsVersion=1",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1&models=gpt-4o",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1&taskTypes=1",
            "?scope=ME&from=2026-11-02&to=2026-11-08&settingsVersion=1&unit=DAY",
            "?scope=TEAM&settingsVersion=1",
            "?scope=TEAM&weekStart=2026-11-02",
            "?scope=TEAM&weekStart=2026-11-03&settingsVersion=1",
            "?scope=TEAM&weekStart=2026-11-02&settingsVersion=99",
            "?scope=TEAM&from=2026-11-02&to=2026-11-08&settingsVersion=1",
            "?scope=TEAM&weekStart=2026-11-02&settingsVersion=1&userId=1"
    })
    @DisplayName("필수 값 누락, 잘못된 값, 없는 settingsVersion, 지원하지 않는 파라미터는 422 VALIDATION_FAILED다")
    void invalidRequest(String query) throws Exception {
        perform(leader, query)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").isNotEmpty());
    }

    @Test
    @DisplayName("TEAM: 진행 중인 주는 422, 방금 끝난 주는 조회할 수 있다")
    void weekMustBeCompleted() throws Exception {
        LocalDate thisMonday = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        perform(leader, TEAM.replace("2026-11-02", thisMonday.toString()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("weekStart"));
        perform(leader, TEAM.replace("2026-11-02", thisMonday.minusWeeks(1).toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401 AUTH_REQUIRED로 응답한다")
    void withoutToken() throws Exception {
        mockMvc.perform(get(url() + ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("API 문서에 v4 조회 조건만 개별 쿼리 파라미터로 노출된다")
    void apiDocsExposeQueryParameters() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/dashboard/kpis'].get.parameters[*].name",
                        containsInAnyOrder("teamId", "scope", "from", "to", "weekStart", "settingsVersion")));
    }

    private AnalysisItem.AnalysisItemBuilder confirmed(AnalysisItem.AnalysisItemBuilder builder) {
        return builder.confirmedOutcome(AnalysisOutcome.SUCCESS).confirmedReworkType(AnalysisReworkType.NONE);
    }

    private void saveItems(int count, User user, AnalysisOutcome outcome, AnalysisReworkType reworkType) {
        for (int index = 0; index < count; index++) {
            fixture.save(fixture.item(settings, user, MONDAY).confirmedOutcome(outcome)
                    .confirmedReworkType(reworkType));
        }
    }

    /** 조회 시각(asOf)을 뺀 TEAM 응답 */
    private Map<String, Object> teamData(User user) throws Exception {
        String body = perform(user, TEAM).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Object> data = JsonPath.read(body, "$.data");
        data.remove("asOf");
        return data;
    }

    private String cohortIdOf(User user, String query) throws Exception {
        return JsonPath.read(perform(user, query).andReturn().getResponse().getContentAsString(), "$.data.cohortId");
    }

    private void expectCohortId(User user, String query, Matcher<String> matcher) throws Exception {
        perform(user, query).andExpect(status().isOk()).andExpect(jsonPath("$.data.cohortId", matcher));
    }

    private ResultActions perform(User user, String query) throws Exception {
        em.flush();
        em.clear();
        return mockMvc.perform(fixture.get(user, url() + query));
    }

    private String url() {
        return "/api/v1/teams/" + team.getId() + "/dashboard/kpis";
    }
}
