package com.babylion.team1.domain.dashboard.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.dashboard.DashboardFixture;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
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
 * v4 명세에 없는 API(온보딩 비교, 비용 구조, 교육 필요 업무, 일자 상세)가 분석이 끝난 대화만 집계하는지 확인한다.
 * 모두 2026-11-05 대화, 호출비 100원.
 *
 *  - 집계: v3 분석 완료, v4 분석 성공, v3 대화를 다시 올려 v4 분석을 기다리는 중
 *  - 제외: v3 분석 중, v3 분석 실패, v4 분석 전·실패(현행 버전 없음), v4 삭제
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(DashboardFixture.class)
class LegacyDashboardAggregationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 5);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private DashboardFixture fixture;

    private User leader;
    private Team team;
    private TeamTaskType taskType;

    @BeforeEach
    void setUp() {
        leader = fixture.user("팀장", "legacy-it-" + UUID.randomUUID() + "@test.com");
        team = fixture.team("기획팀", leader);
        em.persist(TeamMember.leader(team, leader));
        taskType = fixture.taskType(team, "주간 보고서 작성");
        TeamSettings settings = TeamSettings.createDefault(team);
        settings.updateTarget(DAY, DAY, DAY, null);
        em.persist(settings);

        saveConversation(fixture.upload(team, leader), "v3 완료");
        Conversation analyzed = saveConversation(fixture.consumedUpload(team, leader), "v4 성공");
        analyzed.promote(fixture.version(analyzed, 1, null));
        Conversation reuploaded = saveConversation(fixture.upload(team, leader), "v3 재업로드");
        fixture.version(reuploaded, 1, null);

        Upload analyzing = fixture.uploadInProgress(team, leader);
        analyzing.startAnalysis(1);
        saveConversation(analyzing, "v3 분석 중");
        Upload failed = fixture.uploadInProgress(team, leader);
        failed.fail("실패");
        saveConversation(failed, "v3 실패");
        Conversation pending = saveConversation(fixture.consumedUpload(team, leader), "v4 분석 전");
        fixture.version(pending, 1, null);
        Conversation deleted = saveConversation(fixture.consumedUpload(team, leader), "v4 삭제");
        deleted.promote(fixture.version(deleted, 1, Instant.now()));
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("온보딩 비교")
    void onboardingEffect() throws Exception {
        perform("/dashboard/onboarding-effect")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.after.conversationCount").value(3));
    }

    @Test
    @DisplayName("비용 구조 (COST_TYPE·MODEL·TASK_TYPE)")
    void costBreakdown() throws Exception {
        for (String groupBy : new String[] {"COST_TYPE", "MODEL", "TASK_TYPE"}) {
            perform("/dashboard/cost-breakdown?groupBy=" + groupBy)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.points[0].items[0].cost").value(300));
        }
    }

    @Test
    @DisplayName("교육 필요 업무")
    void taskTypes() throws Exception {
        perform("/dashboard/task-types")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].conversationCount").value(3));
    }

    @Test
    @DisplayName("일자 상세의 본인 대화 목록과 업무 유형 집계")
    void dayDetail() throws Exception {
        perform("/days/" + DAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversations[*].title", contains("v3 완료", "v4 성공", "v3 재업로드")))
                .andExpect(jsonPath("$.data.taskTypes[0].count").value(3));
    }

    private Conversation saveConversation(Upload upload, String title) {
        return fixture.save(fixture.conversation(upload, leader, taskType, DAY).title(title)
                .estimatedCost(new BigDecimal("100")));
    }

    private ResultActions perform(String path) throws Exception {
        return mockMvc.perform(fixture.get(leader, "/api/v1/teams/" + team.getId() + path));
    }
}
