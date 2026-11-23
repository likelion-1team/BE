package com.babylion.team1.domain.team.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamSettings;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.team.repository.TeamTaskTypeRepository;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.support.TestAccessTokens;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamSettingsControllerIntegrationTest {

    private static final String SETTINGS_PATH = "/api/v1/teams/{teamId}/settings";
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);
    private static final LocalDate START = LocalDate.of(2026, 11, 15);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @TempDir
    static Path uploadDirectory;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestAccessTokens accessTokens;
    @Autowired
    private TeamSettingsRepository settingsRepository;
    @Autowired
    private TeamTaskTypeRepository taskTypeRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;

    @DynamicPropertySource
    static void configureUploadStorage(DynamicPropertyRegistry properties) {
        properties.add("app.upload.directory", () -> uploadDirectory.toString());
    }

    @Test
    void memberReadsDefaultSettingsAndCannotUpdateThem() throws Exception {
        TeamFixture fixture = saveTeam(true);
        User member = saveUser();
        entityManager.persist(TeamMember.member(fixture.team(), member));

        read(fixture.team().getId(), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.data.taskTypes.length()").value(0))
                .andExpect(jsonPath("$.data.targetFrom").value(nullValue()))
                .andExpect(jsonPath("$.data.targetTo").value(nullValue()))
                .andExpect(jsonPath("$.data.onboardingStartDate").value(nullValue()))
                .andExpect(jsonPath("$.data.targetCount").value(nullValue()))
                .andExpect(jsonPath("$.data.minDailyConversations").value(3))
                .andExpect(jsonPath("$.data.hourlyLaborCost").value(30000));

        update(fixture.team().getId(), member, validRequest("보고서", "검토 통과", 120))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_ONLY"));
    }

    @Test
    void rejectsOutsiderAndMissingTeam() throws Exception {
        TeamFixture fixture = saveTeam(true);
        User outsider = saveTeam(true).leader();
        Map<String, Object> body = validRequest("보고서", "검토 통과", 120);

        read(fixture.team().getId(), outsider).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
        update(fixture.team().getId(), outsider, body).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
        read(Long.MAX_VALUE, outsider).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
        update(Long.MAX_VALUE, outsider, body).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    void requiresAccessTokenAndDocumentsBothOperations() throws Exception {
        mockMvc.perform(get(SETTINGS_PATH, 1L)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(SETTINGS_PATH, 1L).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest("보고서", "검토 통과", 120))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/settings'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/settings'].put.responses['200']").exists());
    }

    @Test
    void leaderSavesAndReadsSettingsWhileKeepingEvaluationDefaults() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long teamId = fixture.team().getId();

        update(teamId, fixture.leader(), validRequest("보고서 작성", "검토 통과", 120))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("설정 저장 성공"))
                .andExpect(jsonPath("$.data.updatedAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.errors").value(nullValue()));

        read(teamId, fixture.leader()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskTypes.length()").value(1))
                .andExpect(jsonPath("$.data.taskTypes[0].id").isNumber())
                .andExpect(jsonPath("$.data.taskTypes[0].name").value("보고서 작성"))
                .andExpect(jsonPath("$.data.taskTypes[0].successDefinition").value("검토 통과"))
                .andExpect(jsonPath("$.data.targetFrom").value(FROM.toString()))
                .andExpect(jsonPath("$.data.targetTo").value(TO.toString()))
                .andExpect(jsonPath("$.data.onboardingStartDate").value(START.toString()))
                .andExpect(jsonPath("$.data.targetCount").value(120))
                .andExpect(jsonPath("$.data.minDailyConversations").value(3))
                .andExpect(jsonPath("$.data.hourlyLaborCost").value(30000));

        update(teamId, fixture.leader(), validRequest("보고서 작성", "검토 통과", null))
                .andExpect(status().isOk());
        read(teamId, fixture.leader()).andExpect(jsonPath("$.data.targetCount").value(nullValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void rejectsInvalidSettings(String description, Map<String, Object> body) throws Exception {
        TeamFixture fixture = saveTeam(true);

        update(fixture.team().getId(), fixture.leader(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(taskTypeRepository.findByTeamIdAndIsActiveTrueOrderById(fixture.team().getId())).isEmpty();
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                invalid("업무 유형 누락", "taskTypes", null),
                invalid("업무 유형 없음", "taskTypes", List.of()),
                invalid("업무 유형 2개", "taskTypes", List.of(
                        Map.of("name", "보고서", "successDefinition", "검토 통과"),
                        Map.of("name", "회의록", "successDefinition", "검토 통과"))),
                invalid("업무 유형 null", "taskTypes", Collections.singletonList(null)),
                invalid("업무 유형명이 51자", "taskTypes",
                        List.of(Map.of("name", "a".repeat(51), "successDefinition", "검토 통과"))),
                invalid("성공 정의가 공백", "taskTypes", List.of(Map.of("name", "보고서", "successDefinition", " "))),
                invalid("성공 정의가 501자", "taskTypes",
                        List.of(Map.of("name", "보고서", "successDefinition", "a".repeat(501)))),
                invalid("시작일 누락", "targetFrom", null),
                invalid("온보딩 시작일 누락", "onboardingStartDate", null),
                invalid("유효하지 않은 날짜", "targetFrom", "2026-11-31"),
                invalid("날짜 형식 오류", "targetTo", "2026/11/30"),
                invalid("기간 역전", "targetFrom", "2026-12-01"),
                invalid("기간 밖 온보딩 시작일", "onboardingStartDate", "2026-12-01"),
                invalid("대상 건수 0", "targetCount", 0),
                invalid("대상 건수 소수", "targetCount", 1.5),
                invalid("대상 건수 범위 초과", "targetCount", 2147483648L));
    }

    @Test
    void changingOnlySuccessDefinitionKeepsIdAndPersistsReturnedTimestamp() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long teamId = fixture.team().getId();
        update(teamId, fixture.leader(), validRequest("보고서", "기존 정의", 120)).andExpect(status().isOk());
        Long taskId = taskType("보고서", teamId).getId();
        LocalDateTime oldUpdate = LocalDateTime.of(2025, 1, 1, 0, 0);
        entityManager.flush();
        jdbc.update("update team_settings set updated_at = ? where team_id = ?", oldUpdate, teamId);
        entityManager.clear();

        String body = update(teamId, fixture.leader(), validRequest("보고서", "수정된 정의", 120))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        OffsetDateTime returned = OffsetDateTime.parse(JsonPath.read(body, "$.data.updatedAt"));
        entityManager.flush();
        entityManager.clear();

        TeamSettings saved = settingsRepository.findByTeamId(teamId).orElseThrow();
        assertThat(saved.getUpdatedAt()).isAfter(oldUpdate).isEqualTo(returned.toLocalDateTime());
        assertThat(returned.getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
        read(teamId, fixture.leader())
                .andExpect(jsonPath("$.data.taskTypes[0].id").value(taskId))
                .andExpect(jsonPath("$.data.taskTypes[0].successDefinition").value("수정된 정의"));
    }

    @Test
    void switchesAndReactivatesTypesWithoutBreakingHistoricalReferences() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long teamId = fixture.team().getId();
        update(teamId, fixture.leader(), validRequest("보고서", "기존 정의", 120)).andExpect(status().isOk());
        TeamTaskType oldType = taskType("보고서", teamId);
        Upload upload = saveUpload(fixture);
        Conversation conversation = saveConversation(fixture, upload, oldType, START, 100);
        Feedback feedback = Feedback.builder().team(fixture.team()).upload(upload).user(fixture.leader())
                .taskType(oldType).feedbackDate(START).type("REWORK").title("주간 보고서")
                .problem("재작업").improvement("입력 개선").occurrenceCount(1).build();
        entityManager.persist(feedback);

        update(teamId, fixture.leader(), validRequest("회의록", "회의 내용 공유", 120)).andExpect(status().isOk());
        read(teamId, fixture.leader()).andExpect(jsonPath("$.data.taskTypes.length()").value(1))
                .andExpect(jsonPath("$.data.taskTypes[0].name").value("회의록"));
        entityManager.flush();
        entityManager.clear();
        assertThat(taskType("보고서", teamId).getIsActive()).isFalse();
        assertThat(entityManager.find(Conversation.class, conversation.getId()).getTaskType().getId())
                .isEqualTo(oldType.getId());
        assertThat(entityManager.find(Feedback.class, feedback.getId()).getTaskType().getId()).isEqualTo(oldType.getId());

        update(teamId, fixture.leader(), validRequest("보고서", "다시 적용", null)).andExpect(status().isOk());
        read(teamId, fixture.leader()).andExpect(jsonPath("$.data.taskTypes.length()").value(1))
                .andExpect(jsonPath("$.data.taskTypes[0].id").value(oldType.getId()))
                .andExpect(jsonPath("$.data.taskTypes[0].successDefinition").value("다시 적용"));
        assertThat(taskType("회의록", teamId).getIsActive()).isFalse();
    }

    @Test
    void uploadUsesTheNewTargetPeriod() throws Exception {
        TeamFixture fixture = saveTeam(true);
        String export = """
                [
                  {"id":"before","title":"Before window","create_time":%d,"mapping":{}},
                  {"id":"first","title":"First day","create_time":%d,"mapping":{}},
                  {"id":"last","title":"Last day","create_time":%d,"mapping":{}},
                  {"id":"after","title":"After window","create_time":%d,"mapping":{}}
                ]
                """.formatted(epoch(FROM.minusDays(1)), epoch(FROM), epoch(TO), epoch(TO.plusDays(1)));
        MockMultipartFile file = new MockMultipartFile("file", "conversations.json", "application/json",
                export.getBytes(StandardCharsets.UTF_8));
        String path = "/api/v1/teams/{teamId}/uploads";
        mockMvc.perform(multipart(path, fixture.team().getId()).file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leader())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TARGET_NOT_SET"));

        update(fixture.team().getId(), fixture.leader(), validRequest("보고서", "검토 통과", 120))
                .andExpect(status().isOk());
        mockMvc.perform(multipart(path, fixture.team().getId()).file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leader())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversations.length()").value(2))
                .andExpect(jsonPath("$.data.conversations[0].externalId").value("first"))
                .andExpect(jsonPath("$.data.conversations[1].externalId").value("last"));
    }

    @Test
    void onboardingDashboardIncludesTheStartDateInAfterPeriod() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long teamId = fixture.team().getId();
        update(teamId, fixture.leader(), validRequest("보고서", "검토 통과", null)).andExpect(status().isOk());
        TeamTaskType type = taskType("보고서", teamId);
        Upload upload = saveUpload(fixture);
        saveConversation(fixture, upload, type, FROM.minusDays(1), 900);
        saveConversation(fixture, upload, type, START.minusDays(1), 200);
        saveConversation(fixture, upload, type, START, 100);

        mockMvc.perform(get("/api/v1/teams/{teamId}/dashboard/onboarding-effect", teamId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leader())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingStartDate").value(START.toString()))
                .andExpect(jsonPath("$.data.before.conversationCount").value(1))
                .andExpect(jsonPath("$.data.before.tokensPerSuccess").value(200))
                .andExpect(jsonPath("$.data.after.conversationCount").value(1))
                .andExpect(jsonPath("$.data.after.tokensPerSuccess").value(100));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFirstUpdatesLeaveOneSettingsRowAndOneMatchingActiveType() throws Exception {
        TeamFixture fixture = transactions.execute(ignored -> saveTeam(false));
        Long teamId = fixture.team().getId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            read(teamId, fixture.leader()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.taskTypes.length()").value(0))
                    .andExpect(jsonPath("$.data.targetFrom").value(nullValue()));
            assertThat(settingsRepository.findByTeamId(teamId)).isEmpty();

            CyclicBarrier start = new CyclicBarrier(2);
            Callable<Integer> report = () -> concurrentUpdate(fixture, start, "보고서", 100);
            Callable<Integer> minutes = () -> concurrentUpdate(fixture, start, "회의록", 200);
            List<Future<Integer>> results = executor.invokeAll(List.of(report, minutes), 20, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactly(200, 200);

            List<TeamTaskType> active = taskTypeRepository.findByTeamIdAndIsActiveTrueOrderById(teamId);
            assertThat(active).hasSize(1);
            TeamSettings settings = settingsRepository.findByTeamId(teamId).orElseThrow();
            int expectedCount = active.get(0).getName().equals("보고서") ? 100 : 200;
            assertThat(settings.getTargetCount()).isEqualTo(expectedCount);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            transactions.executeWithoutResult(ignored -> {
                jdbc.update("delete from team_task_types where team_id = ?", teamId);
                jdbc.update("delete from team_settings where team_id = ?", teamId);
                jdbc.update("delete from team_members where team_id = ?", teamId);
                jdbc.update("delete from teams where id = ?", teamId);
                jdbc.update("delete from users where id = ?", fixture.leader().getId());
            });
        }
    }

    private int concurrentUpdate(TeamFixture fixture, CyclicBarrier start, String name, int targetCount) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return update(fixture.team().getId(), fixture.leader(), validRequest(name, "검토 통과", targetCount))
                .andReturn().getResponse().getStatus();
    }

    private static Arguments invalid(String description, String field, Object value) {
        Map<String, Object> body = validRequest("보고서", "검토 통과", 120);
        if (value == null) {
            body.remove(field);
        } else {
            body.put(field, value);
        }
        return Arguments.of(description, body);
    }

    private static Map<String, Object> validRequest(String name, String successDefinition, Integer targetCount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskTypes", List.of(Map.of("name", name, "successDefinition", successDefinition)));
        body.put("targetFrom", FROM.toString());
        body.put("targetTo", TO.toString());
        body.put("onboardingStartDate", START.toString());
        if (targetCount != null) {
            body.put("targetCount", targetCount);
        }
        return body;
    }

    private TeamFixture saveTeam(boolean createSettings) {
        User leader = saveUser();
        Team team = Team.builder().name("Team settings test").createdBy(leader).build();
        entityManager.persist(team);
        entityManager.persist(TeamMember.leader(team, leader));
        if (createSettings) {
            entityManager.persist(TeamSettings.createDefault(team));
        }
        return new TeamFixture(team, leader);
    }

    private User saveUser() {
        User user = User.builder().name("Test user")
                .email("team-settings-it-" + UUID.randomUUID() + "@example.com").passwordHash("test-password-hash").build();
        entityManager.persist(user);
        return user;
    }

    private TeamTaskType taskType(String name, Long teamId) {
        return taskTypeRepository.findByTeamIdAndName(teamId, name).orElseThrow();
    }

    private Upload saveUpload(TeamFixture fixture) {
        Upload upload = Upload.builder().team(fixture.team()).uploader(fixture.leader())
                .fileName("conversations.json").conversationCount(3).build();
        upload.complete();
        entityManager.persist(upload);
        return upload;
    }

    private Conversation saveConversation(TeamFixture fixture, Upload upload, TeamTaskType type, LocalDate date, int tokens) {
        Conversation conversation = Conversation.builder()
                .team(fixture.team()).upload(upload).user(fixture.leader()).taskType(type)
                .externalId(UUID.randomUUID().toString()).startedAt(date.atTime(12, 0)).conversationDate(date)
                .turnCount(1).estimatedTokens(tokens).isSuccess(true).build();
        entityManager.persist(conversation);
        return conversation;
    }

    private long epoch(LocalDate date) {
        return date.atTime(12, 0).atZone(KST).toEpochSecond();
    }

    private ResultActions read(Long teamId, User user) throws Exception {
        return mockMvc.perform(get(SETTINGS_PATH, teamId).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions update(Long teamId, User user, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put(SETTINGS_PATH, teamId).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private String bearer(User user) {
        return "Bearer " + accessTokens.create(user.getId());
    }

    private record TeamFixture(Team team, User leader) {}
}
