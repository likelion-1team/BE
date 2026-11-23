package com.babylion.team1.domain.dashboard.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.dashboard.DashboardFixture;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대상 기간 2026-11-01 ~ 2026-11-20(20일), 직전 기간 2026-10-12 ~ 2026-10-31.
 *
 * 현재 기간 대화
 *  - member1 / 업무A / gpt-4o      : 성공, 1000토큰, 호출비 0.5, 검토 600초
 *  - member1 / 업무B / gpt-4o-mini : 실패, 500토큰, 호출비 0.25, 수습 300초, has_rework
 *  - member2 / 업무A / gpt-4o      : 성공, 2000토큰, 호출비 1.0
 * 직전 기간 대화
 *  - member1 / 업무A / 단가 없는 모델 : 성공, 800토큰, 호출비 NULL
 * 피드백
 *  - member1 / 업무A / 2026-11-05 : 예상 절감액 1200.50
 *  - member2 / 업무A / 2026-11-06 : 예상 절감액 300.00
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(DashboardFixture.class)
class DashboardControllerTest {

    private static final LocalDate TARGET_FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TARGET_TO = LocalDate.of(2026, 11, 20);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private DashboardFixture fixture;

    private User leader;
    private User member1;
    private User member2;
    private User outsider;
    private Team team;
    private TeamSettings settings;
    private TeamTaskType taskTypeA;
    private TeamTaskType taskTypeB;

    @BeforeEach
    void setUp() {
        // 테스트도 로컬 DB를 쓰므로, 직접 가입해 둔 계정과 겹치지 않게 실행마다 고유한 이메일을 쓴다
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        leader = fixture.user("팀장", "dashboard-it-leader-" + suffix + "@test.com");
        member1 = fixture.user("팀원1", "dashboard-it-member1-" + suffix + "@test.com");
        member2 = fixture.user("팀원2", "dashboard-it-member2-" + suffix + "@test.com");
        outsider = fixture.user("외부인", "dashboard-it-outsider-" + suffix + "@test.com");

        team = fixture.team("기획팀", leader);
        em.persist(TeamMember.leader(team, leader));
        em.persist(TeamMember.member(team, member1));
        em.persist(TeamMember.member(team, member2));

        settings = TeamSettings.createDefault(team);
        settings.updateTarget(TARGET_FROM, TARGET_TO, null, 10);
        em.persist(settings);

        taskTypeA = fixture.taskType(team, "주간 보고서 작성", "수정 없이 제출");
        taskTypeB = fixture.taskType(team, "회의록 정리", "바로 공유");

        Upload upload1 = fixture.upload(team, member1);
        Upload upload2 = fixture.upload(team, member2);

        saveConversation(upload1, member1, taskTypeA, "gpt-4o", LocalDate.of(2026, 11, 3),
                true, false, 1000, new BigDecimal("0.5"), 600, 0);
        saveConversation(upload1, member1, taskTypeB, "gpt-4o-mini", LocalDate.of(2026, 11, 20),
                false, true, 500, new BigDecimal("0.25"), 0, 300);
        saveConversation(upload2, member2, taskTypeA, "gpt-4o", LocalDate.of(2026, 11, 1),
                true, false, 2000, new BigDecimal("1.0"), 0, 0);
        saveConversation(upload1, member1, taskTypeA, "unknown-model", LocalDate.of(2026, 10, 31),
                true, false, 800, null, 0, 0);

        saveFeedback(upload1, member1, taskTypeA, LocalDate.of(2026, 11, 5), new BigDecimal("1200.50"));
        saveFeedback(upload2, member2, taskTypeA, LocalDate.of(2026, 11, 6), new BigDecimal("300.00"));

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("팀원이 조회하면 대상 기간 기준 본인 지표만 응답하고 coverage는 null이다")
    void memberGetsOwnMetrics() throws Exception {
        perform(member1, kpiUrl(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.from").value("2026-11-01"))
                .andExpect(jsonPath("$.data.to").value("2026-11-20"))
                .andExpect(jsonPath("$.data.prevFrom").value("2026-10-12"))
                .andExpect(jsonPath("$.data.prevTo").value("2026-10-31"))
                // (1000 + 500) / 성공 1건
                .andExpect(jsonPath("$.data.tokensPerSuccess.value").value(1500.0))
                .andExpect(jsonPath("$.data.tokensPerSuccess.prevValue").value(800.0))
                .andExpect(jsonPath("$.data.tokensPerSuccess.estimated").value(true))
                .andExpect(jsonPath("$.data.reworkRate.value").value(0.5))
                .andExpect(jsonPath("$.data.reworkRate.prevValue").value(0.0))
                .andExpect(jsonPath("$.data.reworkRate.estimated").value(false))
                .andExpect(jsonPath("$.data.successRate.value").value(0.5))
                .andExpect(jsonPath("$.data.successRate.prevValue").value(1.0))
                // 호출비 0.75 + (600 + 300)초 x 30000원/시간 = 7500.75
                .andExpect(jsonPath("$.data.costPerSuccess.value").value(7500.75))
                .andExpect(jsonPath("$.data.costPerSuccess.prevValue").value(0.0))
                .andExpect(jsonPath("$.data.costPerSuccess.estimated").value(true))
                .andExpect(jsonPath("$.data.savingAmount.value").value(1200.5))
                .andExpect(jsonPath("$.data.savingAmount.prevValue").value(0))
                .andExpect(jsonPath("$.data.savingAmount.estimated").value(true))
                .andExpect(jsonPath("$.data.humanTime.value").value(900))
                .andExpect(jsonPath("$.data.humanTime.prevValue").value(0))
                .andExpect(jsonPath("$.data.conversationCount.value").value(2))
                .andExpect(jsonPath("$.data.conversationCount.prevValue").value(1))
                .andExpect(jsonPath("$.data.coverage").isEmpty());
    }

    @Test
    @DisplayName("팀장이 조회하면 팀 전체 집계와 coverage를 응답한다")
    void leaderGetsTeamMetricsAndCoverage() throws Exception {
        perform(leader, kpiUrl(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationCount.value").value(3))
                // (1000 + 500 + 2000) / 성공 2건
                .andExpect(jsonPath("$.data.tokensPerSuccess.value").value(1750.0))
                .andExpect(jsonPath("$.data.successRate.value").value(0.6667))
                .andExpect(jsonPath("$.data.savingAmount.value").value(1500.5))
                .andExpect(jsonPath("$.data.coverage.submittedMembers").value(2))
                .andExpect(jsonPath("$.data.coverage.totalMembers").value(3))
                .andExpect(jsonPath("$.data.coverage.analyzedConversations").value(3))
                .andExpect(jsonPath("$.data.coverage.targetCount").value(10))
                .andExpect(jsonPath("$.data.coverage.coverageRate").value(0.3));
    }

    @Test
    @DisplayName("팀장이 scope=ME로 조회하면 본인 대화만 집계하고 coverage는 null이다")
    void leaderGetsOwnMetricsWithScopeMe() throws Exception {
        saveConversation(fixture.upload(team, leader), leader, taskTypeA, "gpt-4o", LocalDate.of(2026, 11, 10),
                true, false, 400, new BigDecimal("0.1"), 0, 0);
        em.flush();
        em.clear();

        perform(leader, kpiUrl(team.getId()) + "?scope=ME")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationCount.value").value(1))
                .andExpect(jsonPath("$.data.conversationCount.prevValue").value(0))
                .andExpect(jsonPath("$.data.tokensPerSuccess.value").value(400.0))
                .andExpect(jsonPath("$.data.successRate.value").value(1.0))
                .andExpect(jsonPath("$.data.savingAmount.value").value(0))
                .andExpect(jsonPath("$.data.coverage").isEmpty());

        // scope=TEAM은 생략했을 때와 같은 팀 집계다
        perform(leader, kpiUrl(team.getId()) + "?scope=TEAM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationCount.value").value(4))
                .andExpect(jsonPath("$.data.coverage.submittedMembers").value(3));
    }

    @Test
    @DisplayName("팀원은 scope=ME면 본인 지표를 받고, scope=TEAM이면 403 LEADER_ONLY로 응답한다")
    void memberScope() throws Exception {
        perform(member1, kpiUrl(team.getId()) + "?scope=ME")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationCount.value").value(2))
                .andExpect(jsonPath("$.data.coverage").isEmpty());

        perform(member1, kpiUrl(team.getId()) + "?scope=TEAM")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_ONLY"));
    }

    @Test
    @DisplayName("scope 값이 ME·TEAM이 아니면 400 INVALID_REQUEST로 응답한다")
    void invalidScope() throws Exception {
        perform(leader, kpiUrl(team.getId()) + "?scope=ALL")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("팀장이 업무 유형·모델로 거르면 해당 대화만 집계한다 (쉼표로 여러 개)")
    void leaderFiltersByTaskTypesAndModels() throws Exception {
        perform(leader, kpiUrl(team.getId()) + "?taskTypes=" + taskTypeA.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationCount.value").value(2))
                .andExpect(jsonPath("$.data.successRate.value").value(1.0))
                .andExpect(jsonPath("$.data.coverage.analyzedConversations").value(2));

        perform(leader, kpiUrl(team.getId()) + "?taskTypes=" + taskTypeA.getId() + "," + taskTypeB.getId()
                + "&models=gpt-4o-mini,o3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationCount.value").value(1))
                .andExpect(jsonPath("$.data.reworkRate.value").value(1.0));
    }

    @Test
    @DisplayName("from·to를 넣으면 그 기간과 직전 같은 길이 기간으로 집계한다")
    void customPeriod() throws Exception {
        perform(member1, kpiUrl(team.getId()) + "?from=2026-11-03&to=2026-11-03&unit=WEEK")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prevFrom").value("2026-11-02"))
                .andExpect(jsonPath("$.data.prevTo").value("2026-11-02"))
                .andExpect(jsonPath("$.data.conversationCount.value").value(1))
                .andExpect(jsonPath("$.data.conversationCount.prevValue").value(0));
    }

    @Test
    @DisplayName("targetCount가 없으면 coverageRate는 null이다")
    void coverageRateNullWithoutTargetCount() throws Exception {
        TeamSettings found = em.find(TeamSettings.class, settings.getId());
        found.updateTarget(TARGET_FROM, TARGET_TO, null, null);
        em.flush();
        em.clear();

        perform(leader, kpiUrl(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverage.analyzedConversations").value(3))
                .andExpect(jsonPath("$.data.coverage.targetCount").isEmpty())
                .andExpect(jsonPath("$.data.coverage.coverageRate").isEmpty());
    }

    @Test
    @DisplayName("대화가 0건이면 비율 지표는 null, 합계 지표는 0으로 응답한다")
    void noConversations() throws Exception {
        perform(member1, kpiUrl(team.getId()) + "?from=2026-09-01&to=2026-09-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokensPerSuccess.value").isEmpty())
                .andExpect(jsonPath("$.data.tokensPerSuccess.prevValue").isEmpty())
                .andExpect(jsonPath("$.data.reworkRate.value").isEmpty())
                .andExpect(jsonPath("$.data.successRate.value").isEmpty())
                .andExpect(jsonPath("$.data.costPerSuccess.value").isEmpty())
                .andExpect(jsonPath("$.data.savingAmount.value").value(0))
                .andExpect(jsonPath("$.data.humanTime.value").value(0))
                .andExpect(jsonPath("$.data.conversationCount.value").value(0));
    }

    @Test
    @DisplayName("팀 멤버가 아니면 403 NOT_A_MEMBER로 응답한다")
    void notAMember() throws Exception {
        perform(outsider, kpiUrl(team.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
    }

    @Test
    @DisplayName("없는 팀이면 404 TEAM_NOT_FOUND로 응답한다")
    void teamNotFound() throws Exception {
        perform(leader, kpiUrl(team.getId() + 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("from이 to보다 늦으면 400 INVALID_REQUEST로 응답한다")
    void fromAfterTo() throws Exception {
        perform(member1, kpiUrl(team.getId()) + "?from=2026-11-20&to=2026-11-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("unit·날짜·taskTypes 형식이 잘못되면 400 INVALID_REQUEST로 응답한다")
    void invalidParameterFormat() throws Exception {
        perform(member1, kpiUrl(team.getId()) + "?unit=MONTH")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        perform(member1, kpiUrl(team.getId()) + "?from=2026/11/01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        perform(member1, kpiUrl(team.getId()) + "?taskTypes=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("기간을 넣지 않았는데 팀 대상 기간도 없으면 400 TARGET_NOT_SET으로 응답한다")
    void noPeriodAndTargetNotSet() throws Exception {
        TeamSettings found = em.find(TeamSettings.class, settings.getId());
        found.updateTarget(null, null, null, null);
        em.flush();
        em.clear();

        perform(member1, kpiUrl(team.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_SET"));

        // 기간을 직접 넣으면 대상 기간이 없어도 조회된다
        perform(member1, kpiUrl(team.getId()) + "?from=2026-11-01&to=2026-11-20")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401 AUTH_REQUIRED로 응답한다")
    void withoutToken() throws Exception {
        mockMvc.perform(get(kpiUrl(team.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("API 문서에 조회 조건이 개별 쿼리 파라미터로 노출된다")
    void apiDocsExposeQueryParameters() throws Exception {
        String params = "$.paths['/api/v1/teams/{teamId}/dashboard/kpis'].get.parameters";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(params + "[?(@.name == 'from')].in").value("query"))
                .andExpect(jsonPath(params + "[?(@.name == 'to')].in").value("query"))
                .andExpect(jsonPath(params + "[?(@.name == 'unit')].in").value("query"))
                .andExpect(jsonPath(params + "[?(@.name == 'taskTypes')].in").value("query"))
                .andExpect(jsonPath(params + "[?(@.name == 'models')].in").value("query"))
                .andExpect(jsonPath(params + "[?(@.name == 'scope')].in").value("query"))
                .andExpect(jsonPath(params + "[?(@.name == 'request')]").isEmpty());
    }

    @Test
    @DisplayName("Swagger처럼 배열을 같은 이름으로 반복해 보내도 필터가 적용된다")
    void repeatedArrayParameters() throws Exception {
        perform(leader, kpiUrl(team.getId()) + "?taskTypes=" + taskTypeA.getId() + "&taskTypes=" + taskTypeB.getId()
                + "&models=gpt-4o-mini&models=o3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationCount.value").value(1));
    }

    private ResultActions perform(User user, String url) throws Exception {
        return mockMvc.perform(fixture.get(user, url));
    }

    private String kpiUrl(Long teamId) {
        return "/api/v1/teams/" + teamId + "/dashboard/kpis";
    }

    private void saveConversation(Upload upload, User user, TeamTaskType taskType, String model, LocalDate date,
            boolean success, boolean rework, int tokens, BigDecimal cost, int reviewSec, int reworkSec) {
        fixture.save(fixture.conversation(upload, user, taskType, date)
                .model(model).estimatedTokens(tokens).estimatedCost(cost)
                .isSuccess(success).hasRework(rework).reviewTimeSec(reviewSec).reworkTimeSec(reworkSec));
    }

    private void saveFeedback(Upload upload, User user, TeamTaskType taskType, LocalDate date, BigDecimal saving) {
        fixture.save(fixture.feedback(upload, user, taskType, date)
                .title("요청이 모호함").expectedSavingCost(saving));
    }
}
