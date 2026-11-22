package com.babylion.team1.domain.dashboard.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.dashboard.DashboardFixture;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import jakarta.persistence.EntityManager;
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
 * 업무 유형 A, B, C, D(D는 대화 없음)
 *
 *  - member1 / A / gpt-4o      / 2026-11-03
 *  - member1 / B / o3          / 2026-11-10
 *  - member1 / A / model NULL  / 2026-11-12
 *  - member2 / A / gpt-4o-mini / 2026-10-28
 *  - member2 / C / gpt-4o      / 2026-11-20
 *  - member3 : 대화 없음
 *  - 다른 팀 대화 (model: other-team-model, 2026-01-01) : 어디에도 나오면 안 됨
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
    private User member1;
    private User member3;
    private User outsider;
    private Team team;
    private TeamTaskType taskTypeA;
    private TeamTaskType taskTypeB;
    private TeamTaskType taskTypeC;

    @BeforeEach
    void setUp() {
        // 테스트도 로컬 DB를 쓰므로, 직접 가입해 둔 계정과 겹치지 않게 실행마다 고유한 이메일을 쓴다
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        leader = fixture.user("팀장", "filter-it-leader-" + suffix + "@test.com");
        member1 = fixture.user("팀원1", "filter-it-member1-" + suffix + "@test.com");
        User member2 = fixture.user("팀원2", "filter-it-member2-" + suffix + "@test.com");
        member3 = fixture.user("팀원3", "filter-it-member3-" + suffix + "@test.com");
        outsider = fixture.user("외부인", "filter-it-outsider-" + suffix + "@test.com");

        team = fixture.team("기획팀", leader);
        em.persist(TeamMember.leader(team, leader));
        em.persist(TeamMember.member(team, member1));
        em.persist(TeamMember.member(team, member2));
        em.persist(TeamMember.member(team, member3));

        taskTypeA = fixture.taskType(team, "주간 보고서 작성");
        taskTypeB = fixture.taskType(team, "회의록 정리");
        taskTypeC = fixture.taskType(team, "고객 응대");
        fixture.taskType(team, "대화 없는 업무");

        Upload upload1 = fixture.upload(team, member1);
        Upload upload2 = fixture.upload(team, member2);
        saveConversation(upload1, member1, taskTypeA, "gpt-4o", LocalDate.of(2026, 11, 3));
        saveConversation(upload1, member1, taskTypeB, "o3", LocalDate.of(2026, 11, 10));
        saveConversation(upload1, member1, taskTypeA, null, LocalDate.of(2026, 11, 12));
        saveConversation(upload2, member2, taskTypeA, "gpt-4o-mini", LocalDate.of(2026, 10, 28));
        saveConversation(upload2, member2, taskTypeC, "gpt-4o", LocalDate.of(2026, 11, 20));

        // 다른 팀: outsider가 팀장
        Team otherTeam = fixture.team("다른 팀", outsider);
        em.persist(TeamMember.leader(otherTeam, outsider));
        TeamTaskType otherTaskType = fixture.taskType(otherTeam, "다른 팀 업무");
        saveConversation(fixture.upload(otherTeam, outsider), outsider, otherTaskType,
                "other-team-model", LocalDate.of(2026, 1, 1));

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("팀원이 조회하면 본인 대화에 등장한 업무 유형·모델·기간만 응답한다 (model NULL 제외)")
    void memberGetsOwnOptions() throws Exception {
        perform(member1, url(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.taskTypes", hasSize(2)))
                .andExpect(jsonPath("$.data.taskTypes[0].id").value(taskTypeA.getId()))
                .andExpect(jsonPath("$.data.taskTypes[0].name").value("주간 보고서 작성"))
                .andExpect(jsonPath("$.data.taskTypes[1].id").value(taskTypeB.getId()))
                .andExpect(jsonPath("$.data.taskTypes[1].name").value("회의록 정리"))
                .andExpect(jsonPath("$.data.models", contains("gpt-4o", "o3")))
                .andExpect(jsonPath("$.data.dataFrom").value("2026-11-03"))
                .andExpect(jsonPath("$.data.dataTo").value("2026-11-12"));
    }

    @Test
    @DisplayName("팀장이 조회하면 팀 전체 대화 기준으로 응답하고, 다른 팀 데이터는 섞이지 않는다")
    void leaderGetsTeamOptions() throws Exception {
        perform(leader, url(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskTypes", hasSize(3)))
                .andExpect(jsonPath("$.data.taskTypes[0].id").value(taskTypeA.getId()))
                .andExpect(jsonPath("$.data.taskTypes[1].id").value(taskTypeB.getId()))
                .andExpect(jsonPath("$.data.taskTypes[2].id").value(taskTypeC.getId()))
                .andExpect(jsonPath("$.data.models", contains("gpt-4o", "gpt-4o-mini", "o3")))
                .andExpect(jsonPath("$.data.dataFrom").value("2026-10-28"))
                .andExpect(jsonPath("$.data.dataTo").value("2026-11-20"));
    }

    @Test
    @DisplayName("데이터가 없으면 빈 목록과 null 기간을 응답한다")
    void noData() throws Exception {
        perform(member3, url(team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskTypes", hasSize(0)))
                .andExpect(jsonPath("$.data.models", hasSize(0)))
                .andExpect(jsonPath("$.data.dataFrom").isEmpty())
                .andExpect(jsonPath("$.data.dataTo").isEmpty());
    }

    @Test
    @DisplayName("팀 멤버가 아니면 403 NOT_A_MEMBER, 없는 팀이면 404 TEAM_NOT_FOUND로 응답한다")
    void accessDenied() throws Exception {
        perform(outsider, url(team.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));

        perform(leader, url(team.getId() + 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401 UNAUTHORIZED로 응답한다")
    void withoutToken() throws Exception {
        mockMvc.perform(get(url(team.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions perform(User user, String url) throws Exception {
        return mockMvc.perform(fixture.get(user, url));
    }

    private String url(Long teamId) {
        return "/api/v1/teams/" + teamId + "/filter-options";
    }

    /** 대화의 팀은 upload의 팀을 따른다 */
    private void saveConversation(Upload upload, User user, TeamTaskType taskType, String model, LocalDate date) {
        fixture.save(fixture.conversation(upload, user, taskType, date).model(model));
    }
}
