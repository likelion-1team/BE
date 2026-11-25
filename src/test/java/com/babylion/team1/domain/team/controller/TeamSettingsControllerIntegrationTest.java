package com.babylion.team1.domain.team.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
                .andExpect(jsonPath("$.data.length()").value(8))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.taskType").value(nullValue()))
                .andExpect(jsonPath("$.data.targetFrom").value(nullValue()))
                .andExpect(jsonPath("$.data.targetTo").value(nullValue()))
                .andExpect(jsonPath("$.data.onboardingStartDate").value(nullValue()))
                .andExpect(jsonPath("$.data.targetCount").value(nullValue()))
                .andExpect(jsonPath("$.data.updatedAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.data.policies.length()").value(6))
                .andExpect(jsonPath("$.data.policies.policyVersion").value(1))
                .andExpect(jsonPath("$.data.policies.minSampleSize").value(20))
                .andExpect(jsonPath("$.data.policies.minTeamContributors").value(5))
                .andExpect(jsonPath("$.data.policies.minOutcomeConfirmationRate").value(0.8))
                .andExpect(jsonPath("$.data.policies.uploadRetentionMinutes").value(60))
                .andExpect(jsonPath("$.data.policies.maxSelectionsPerRequest").value(100));

        update(fixture.team().getId(), member, validRequest(0, "보고서"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_FORBIDDEN"));
    }

    @Test
    void readsUnsetSettingsWhenTeamHasNoSettingsRow() throws Exception {
        TeamFixture fixture = saveTeam(false);

        read(fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.taskType").value(nullValue()))
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.policies.policyVersion").value(1));
    }

    @Test
    void initialVersionScriptMovesOnlyConfiguredV3SettingsToVersionOne() throws Exception {
        TeamFixture configured = saveV3Team(true, true);
        TeamFixture withoutPeriod = saveV3Team(false, true);
        TeamFixture withoutTaskType = saveV3Team(true, false);
        TeamFixture versioned = saveTeam(true);
        update(versioned.team().getId(), versioned.leader(), validRequest(0, "보고서")).andExpect(status().isOk());
        entityManager.flush();

        String script = Files.readString(Path.of("scripts/migrate-team-settings-initial-version.sql"));
        jdbc.execute(script.strip().replaceAll(";$", ""));
        entityManager.clear();

        read(configured.team().getId(), configured.leader()).andExpect(jsonPath("$.data.version").value(1));
        read(withoutPeriod.team().getId(), withoutPeriod.leader()).andExpect(jsonPath("$.data.version").value(0));
        read(withoutTaskType.team().getId(), withoutTaskType.leader()).andExpect(jsonPath("$.data.version").value(0));
        assertThat(jdbc.queryForList("select version from team_settings where team_id = ? order by version",
                Integer.class, versioned.team().getId())).containsExactly(0, 1);
    }

    @Test
    void rejectsOutsiderAndMissingTeam() throws Exception {
        TeamFixture fixture = saveTeam(true);
        User outsider = saveTeam(true).leader();
        Map<String, Object> body = validRequest(0, "보고서");

        read(fixture.team().getId(), outsider).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        update(fixture.team().getId(), outsider, body).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        read(Long.MAX_VALUE, outsider).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        update(Long.MAX_VALUE, outsider, body).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void requiresAccessTokenAndDocumentsBothOperations() throws Exception {
        mockMvc.perform(get(SETTINGS_PATH, 1L)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(SETTINGS_PATH, 1L).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest(0, "보고서"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/settings'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/settings'].put.responses['200']").exists());
    }

    @Test
    void leaderSavesAndReadsSettings() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long teamId = fixture.team().getId();

        String saved = update(teamId, fixture.leader(), validRequest(0, "보고서 작성"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("설정 저장 성공"))
                .andExpect(jsonPath("$.data.length()").value(8))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.taskType.length()").value(5))
                .andExpect(jsonPath("$.data.taskType.taskTypeId").isNumber())
                .andExpect(jsonPath("$.data.taskType.name").value("보고서 작성"))
                .andExpect(jsonPath("$.data.taskType.successConditions", contains("필수 항목 포함", "수치 일치")))
                .andExpect(jsonPath("$.data.taskType.failureConditions", contains("핵심 수치 오류")))
                .andExpect(jsonPath("$.data.taskType.unknownConditions", contains("근거 없음")))
                .andExpect(jsonPath("$.data.targetFrom").value(FROM.toString()))
                .andExpect(jsonPath("$.data.targetTo").value(TO.toString()))
                .andExpect(jsonPath("$.data.onboardingStartDate").value(START.toString()))
                .andExpect(jsonPath("$.data.targetCount").value(120))
                .andExpect(jsonPath("$.data.updatedAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.data.policies.policyVersion").value(1))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        clear();

        String found = read(teamId, fixture.leader()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<Object>read(found, "$.data")).isEqualTo(JsonPath.read(saved, "$.data"));

        TeamSettings settings = settingsRepository.findByTeamId(teamId).orElseThrow();
        assertThat(settings.getPolicyVersion()).isEqualTo(1);
        assertThat(settings.getCreatedBy().getId()).isEqualTo(fixture.leader().getId());
        assertThat(settings.getTaskType().getSuccessDefinition()).isEqualTo("필수 항목 포함\n수치 일치");
    }

    @Test
    void replacesOmittedOptionalFieldsWithNull() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long teamId = fixture.team().getId();
        update(teamId, fixture.leader(), validRequest(0, "보고서")).andExpect(status().isOk());

        Map<String, Object> omitted = validRequest(1, "보고서");
        omitted.remove("onboardingStartDate");
        omitted.remove("targetCount");
        update(teamId, fixture.leader(), omitted).andExpect(status().isOk());
        read(teamId, fixture.leader())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.onboardingStartDate").value(nullValue()))
                .andExpect(jsonPath("$.data.targetCount").value(nullValue()));

        Map<String, Object> explicitNull = validRequest(2, "보고서");
        explicitNull.put("onboardingStartDate", null);
        explicitNull.put("targetCount", null);
        update(teamId, fixture.leader(), explicitNull).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.onboardingStartDate").value(nullValue()))
                .andExpect(jsonPath("$.data.targetCount").value(nullValue()));
    }

    @Test
    void rejectsDifferentVersionWithCurrentVersion() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long teamId = fixture.team().getId();

        update(teamId, fixture.leader(), validRequest(1, "보고서"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.errors.currentVersion").value(0));
        update(teamId, fixture.leader(), validRequest(0, "보고서")).andExpect(status().isOk());
        update(teamId, fixture.leader(), validRequest(0, "회의록"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errors.currentVersion").value(1));

        read(teamId, fixture.leader())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.taskType.name").value("보고서"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void rejectsInvalidSettings(String description, Map<String, Object> body) throws Exception {
        TeamFixture fixture = saveTeam(true);

        update(fixture.team().getId(), fixture.leader(), body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        read(fixture.team().getId(), fixture.leader()).andExpect(jsonPath("$.data.version").value(0));
        assertThat(taskTypeRepository.findByTeamIdAndIsActiveTrueOrderById(fixture.team().getId())).isEmpty();
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                invalid("버전 누락", body -> body.remove("expectedVersion")),
                invalid("버전 음수", body -> body.put("expectedVersion", -1)),
                invalid("버전 소수", body -> body.put("expectedVersion", 0.5)),
                invalid("업무 유형 누락", body -> body.remove("taskType")),
                invalid("업무 유형이 목록", body -> body.put("taskType", List.of(taskTypeRequest("보고서")))),
                invalidTaskType("이름 누락", type -> type.remove("name")),
                invalidTaskType("이름 공백", type -> type.put("name", " ")),
                invalidTaskType("이름 201자", type -> type.put("name", "가".repeat(201))),
                invalidTaskType("성공 조건 누락", type -> type.remove("successConditions")),
                invalidTaskType("실패 조건 누락", type -> type.remove("failureConditions")),
                invalidTaskType("판정 불가 조건 누락", type -> type.remove("unknownConditions")),
                invalidTaskType("성공 조건 0개", type -> type.put("successConditions", List.of())),
                invalidTaskType("실패 조건 0개", type -> type.put("failureConditions", List.of())),
                invalidTaskType("판정 불가 조건 0개", type -> type.put("unknownConditions", List.of())),
                invalidTaskType("성공 조건 11개",
                        type -> type.put("successConditions", Collections.nCopies(11, "조건"))),
                invalidTaskType("실패 조건 11개",
                        type -> type.put("failureConditions", Collections.nCopies(11, "조건"))),
                invalidTaskType("판정 불가 조건 11개",
                        type -> type.put("unknownConditions", Collections.nCopies(11, "조건"))),
                invalidTaskType("성공 조건 501자",
                        type -> type.put("successConditions", List.of("가".repeat(501)))),
                invalidTaskType("실패 조건 501자",
                        type -> type.put("failureConditions", List.of("가".repeat(501)))),
                invalidTaskType("판정 불가 조건 501자",
                        type -> type.put("unknownConditions", List.of("가".repeat(501)))),
                invalidTaskType("조건 공백", type -> type.put("failureConditions", List.of(" "))),
                invalidTaskType("조건 null", type -> type.put("unknownConditions", Collections.singletonList(null))),
                invalidTaskType("조건이 문자열", type -> type.put("successConditions", "필수 항목 포함")),
                invalidTaskType("업무 유형의 추가 필드", type -> type.put("taskTypeId", 1)),
                invalid("시작일 누락", body -> body.remove("targetFrom")),
                invalid("종료일 누락", body -> body.remove("targetTo")),
                invalid("없는 날짜", body -> body.put("targetFrom", "2026-11-31")),
                invalid("날짜 형식 오류", body -> body.put("targetTo", "2026/11/30")),
                invalid("기간 역전", body -> body.put("targetFrom", "2026-12-01")),
                invalid("기간 367일", body -> body.put("targetTo", FROM.plusDays(366).toString())),
                invalid("온보딩 시작일이 빈 문자열", body -> body.put("onboardingStartDate", "")),
                invalid("온보딩 시작일이 기간 전", body -> body.put("onboardingStartDate", FROM.minusDays(1).toString())),
                invalid("온보딩 시작일이 기간 후", body -> body.put("onboardingStartDate", TO.plusDays(1).toString())),
                invalid("대상 건수 0", body -> body.put("targetCount", 0)),
                invalid("대상 건수 소수", body -> body.put("targetCount", 1.5)),
                invalid("대상 건수 범위 초과", body -> body.put("targetCount", 2147483648L)),
                invalid("대상 건수가 문자", body -> body.put("targetCount", "많음")),
                invalid("정책값 수정", body -> body.put("policies", Map.of("minSampleSize", 1))),
                invalid("허용하지 않은 필드", body -> body.put("version", 0)),
                invalid("v3 요청", body -> {
                    body.remove("taskType");
                    body.put("taskTypes", List.of(Map.of("name", "보고서", "successDefinition", "검토 통과")));
                }));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{\"taskType\":{\"name\":", "{\"taskType\":{\"successConditions\":[\"조건\",}}"})
    void rejectsMalformedJson(String body) throws Exception {
        TeamFixture fixture = saveTeam(true);

        mockMvc.perform(put(SETTINGS_PATH, fixture.team().getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leader()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test
    void acceptsPeriodAndTargetCountBoundaries() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long teamId = fixture.team().getId();

        Map<String, Object> oneDay = validRequest(0, "보고서");
        oneDay.put("targetTo", FROM.toString());
        oneDay.put("onboardingStartDate", FROM.toString());
        oneDay.put("targetCount", 1);
        update(teamId, fixture.leader(), oneDay).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetCount").value(1));

        Map<String, Object> longest = validRequest(1, "보고서");
        longest.put("targetTo", FROM.plusDays(365).toString());
        longest.put("onboardingStartDate", FROM.plusDays(365).toString());
        longest.put("targetCount", Integer.MAX_VALUE);
        update(teamId, fixture.leader(), longest).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetCount").value(Integer.MAX_VALUE));
    }

    @Test
    void keepsTaskTypeIdOnlyWhenNameAndConditionsAreSame() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long teamId = fixture.team().getId();
        Long first = saveAndGetTaskTypeId(fixture, validRequest(0, "보고서"));

        Map<String, Object> changedPeriod = validRequest(1, "보고서");
        changedPeriod.put("targetTo", TO.plusDays(1).toString());
        assertThat(saveAndGetTaskTypeId(fixture, changedPeriod)).isEqualTo(first);

        Long previous = first;
        int version = 2;
        for (String field : List.of("successConditions", "failureConditions", "unknownConditions")) {
            Map<String, Object> type = taskTypeRequest("보고서");
            type.put(field, List.of("바뀐 조건"));
            Map<String, Object> body = validRequest(version++, "보고서");
            body.put("taskType", type);
            Long changed = saveAndGetTaskTypeId(fixture, body);
            assertThat(changed).as(field).isNotEqualTo(previous);
            previous = changed;
        }

        Long renamed = saveAndGetTaskTypeId(fixture, validRequest(version++, "회의록"));
        Long restored = saveAndGetTaskTypeId(fixture, validRequest(version, "보고서"));
        assertThat(restored).isNotIn(first, renamed);
        assertThat(taskTypeRepository.findByTeamIdAndIsActiveTrueOrderById(teamId))
                .extracting(TeamTaskType::getId).containsExactly(restored);
        assertThat(jdbc.queryForList("select task_type_id from team_settings where team_id = ? and version in (1, 2)",
                Long.class, teamId)).containsExactly(first, first);
        TeamTaskType original = entityManager.find(TeamTaskType.class, first);
        assertThat(original.getName()).isEqualTo("보고서");
        assertThat(original.getSuccessConditions()).containsExactly("필수 항목 포함", "수치 일치");
    }

    @Test
    void changingTypeKeepsHistoricalReferences() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long oldTypeId = saveAndGetTaskTypeId(fixture, validRequest(0, "보고서"));
        TeamTaskType oldType = entityManager.find(TeamTaskType.class, oldTypeId);
        User leader = entityManager.find(User.class, fixture.leader().getId());
        Team team = entityManager.find(Team.class, fixture.team().getId());
        TeamFixture reloaded = new TeamFixture(team, leader);
        Upload upload = saveUpload(reloaded);
        Conversation conversation = saveConversation(reloaded, upload, oldType, START, 100);
        Feedback feedback = Feedback.builder().team(team).upload(upload).user(leader)
                .taskType(oldType).feedbackDate(START).type("REWORK").title("주간 보고서")
                .problem("재작업").improvement("입력 개선").occurrenceCount(1).build();
        entityManager.persist(feedback);

        saveAndGetTaskTypeId(fixture, validRequest(1, "회의록"));

        assertThat(entityManager.find(TeamTaskType.class, oldTypeId).getIsActive()).isFalse();
        assertThat(entityManager.find(Conversation.class, conversation.getId()).getTaskType().getId())
                .isEqualTo(oldTypeId);
        assertThat(entityManager.find(Feedback.class, feedback.getId()).getTaskType().getId()).isEqualTo(oldTypeId);
    }

    @Test
    void longestCriteriaRunThroughUploadAndAnalysisStartAndStayAfterChange() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long teamId = fixture.team().getId();
        String name = "가".repeat(200);
        List<String> conditions = IntStream.range(0, 10).mapToObj(index -> index + "나".repeat(499)).toList();
        Map<String, Object> body = validRequest(0, name);
        body.put("taskType", new LinkedHashMap<>(Map.of("name", name, "successConditions", conditions,
                "failureConditions", conditions, "unknownConditions", conditions)));
        Long taskTypeId = saveAndGetTaskTypeId(fixture, body);
        read(teamId, fixture.leader())
                .andExpect(jsonPath("$.data.taskType.name").value(name))
                .andExpect(jsonPath("$.data.taskType.successConditions", contains(conditions.toArray())))
                .andExpect(jsonPath("$.data.taskType.failureConditions", contains(conditions.toArray())))
                .andExpect(jsonPath("$.data.taskType.unknownConditions", contains(conditions.toArray())));

        long epoch = epoch(START);
        String export = """
                [{"id":"%s","title":"테스트","create_time":%d,"current_node":"answer","mapping":{
                  "question":{"parent":null,"children":["answer"],"message":{
                    "author":{"role":"user"},"content":{"parts":["hello"]},"create_time":%d}},
                  "answer":{"parent":"question","children":[],"message":{
                    "author":{"role":"assistant"},"content":{"parts":["world"]},"create_time":%d}}
                }}]
                """.formatted(UUID.randomUUID(), epoch, epoch, epoch + 1);
        mockMvc.perform(multipart("/api/v1/teams/{teamId}/uploads", teamId).param("settingsVersion", "1")
                        .file(new MockMultipartFile("file", "conversations.json", "application/json",
                                export.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leader())))
                .andExpect(status().isCreated());
        clear();
        Long uploadId = jdbc.queryForObject("select id from uploads where team_id = ?", Long.class, teamId);
        String selectionId = jdbc.queryForObject(
                "select selection_id from upload_candidates where upload_id = ?", String.class, uploadId);
        mockMvc.perform(post("/api/v1/uploads/{uploadId}/analyses", uploadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leader()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"settingsVersion":1,"selections":[{"selectionId":"%s","taskTypeId":%d}]}
                                """.formatted(selectionId, taskTypeId)))
                .andExpect(status().isAccepted());
        clear();

        saveAndGetTaskTypeId(fixture, validRequest(1, "회의록"));

        Map<String, Object> item = jdbc.queryForMap("""
                select i.task_type_id, i.task_name_snapshot, i.success_definition_snapshot, s.version
                from analysis_items i join analyses a on a.id = i.analysis_id join team_settings s on s.id = a.settings_id
                where a.team_id = ?
                """, teamId);
        assertThat(item).containsEntry("task_type_id", taskTypeId)
                .containsEntry("task_name_snapshot", name)
                .containsEntry("success_definition_snapshot", String.join("\n", conditions))
                .containsEntry("version", 1);
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
        mockMvc.perform(multipart(path, fixture.team().getId()).file(file).param("settingsVersion", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leader())))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        update(fixture.team().getId(), fixture.leader(), validRequest(0, "보고서")).andExpect(status().isOk());
        mockMvc.perform(multipart(path, fixture.team().getId()).file(file).param("settingsVersion", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(fixture.leader())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.eligibleConversationCount").value(2))
                .andExpect(jsonPath("$.data.conversations").doesNotExist());
    }

    @Test
    void onboardingDashboardIncludesTheStartDateInAfterPeriod() throws Exception {
        TeamFixture fixture = saveTeam(true);
        Long teamId = fixture.team().getId();
        update(teamId, fixture.leader(), validRequest(0, "보고서")).andExpect(status().isOk());
        TeamTaskType type = settingsRepository.findByTeamId(teamId).orElseThrow().getTaskType();
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
    void onlyOneOfConcurrentUpdatesWithSameVersionSucceeds() throws Exception {
        TeamFixture fixture = transactions.execute(ignored -> saveTeam(false));
        Long teamId = fixture.team().getId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier start = new CyclicBarrier(2);
            Callable<Integer> report = () -> concurrentUpdate(fixture, start, "보고서");
            Callable<Integer> minutes = () -> concurrentUpdate(fixture, start, "회의록");
            List<Future<Integer>> results = executor.invokeAll(List.of(report, minutes), 20, TimeUnit.SECONDS);
            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);

            assertThat(jdbc.queryForList(
                    "select version from team_settings where team_id = ?", Integer.class, teamId)).containsExactly(1);
            assertThat(jdbc.queryForList(
                    "select name from team_task_types where team_id = ?", String.class, teamId))
                    .containsExactly(statuses.get(0) == 200 ? "보고서" : "회의록");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            transactions.executeWithoutResult(ignored -> {
                jdbc.update("delete from team_settings where team_id = ?", teamId);
                jdbc.update("delete from team_task_types where team_id = ?", teamId);
                jdbc.update("delete from team_members where team_id = ?", teamId);
                jdbc.update("delete from teams where id = ?", teamId);
                jdbc.update("delete from users where id = ?", fixture.leader().getId());
            });
        }
    }

    private int concurrentUpdate(TeamFixture fixture, CyclicBarrier start, String name) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return update(fixture.team().getId(), fixture.leader(), validRequest(0, name))
                .andReturn().getResponse().getStatus();
    }

    private Long saveAndGetTaskTypeId(TeamFixture fixture, Map<String, Object> body) throws Exception {
        String response = update(fixture.team().getId(), fixture.leader(), body).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        clear();
        return JsonPath.parse(response).read("$.data.taskType.taskTypeId", Long.class);
    }

    private void clear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static Arguments invalid(String description, Consumer<Map<String, Object>> change) {
        Map<String, Object> body = validRequest(0, "보고서");
        change.accept(body);
        return Arguments.of(description, body);
    }

    private static Arguments invalidTaskType(String description, Consumer<Map<String, Object>> change) {
        Map<String, Object> type = taskTypeRequest("보고서");
        change.accept(type);
        return invalid(description, body -> body.put("taskType", type));
    }

    private static Map<String, Object> validRequest(int expectedVersion, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expectedVersion", expectedVersion);
        body.put("taskType", taskTypeRequest(name));
        body.put("targetFrom", FROM.toString());
        body.put("targetTo", TO.toString());
        body.put("onboardingStartDate", START.toString());
        body.put("targetCount", 120);
        return body;
    }

    private static Map<String, Object> taskTypeRequest(String name) {
        Map<String, Object> type = new LinkedHashMap<>();
        type.put("name", name);
        type.put("successConditions", List.of("필수 항목 포함", "수치 일치"));
        type.put("failureConditions", List.of("핵심 수치 오류"));
        type.put("unknownConditions", List.of("근거 없음"));
        return type;
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

    /** v3에서 저장해 version 0으로 남은 설정 */
    private TeamFixture saveV3Team(boolean withPeriod, boolean withTaskType) {
        TeamFixture fixture = saveTeam(false);
        TeamSettings settings = TeamSettings.createDefault(fixture.team());
        if (withPeriod) {
            settings.updateTarget(FROM, TO, START, 120);
        }
        entityManager.persist(settings);
        if (withTaskType) {
            entityManager.persist(TeamTaskType.builder().team(fixture.team())
                    .name("보고서").successConditions(List.of("검토 통과"))
                            .failureConditions(List.of()).unknownConditions(List.of()).build());
        }
        return fixture;
    }

    private User saveUser() {
        User user = User.builder().name("Test user")
                .email("team-settings-it-" + UUID.randomUUID() + "@example.com").passwordHash("test-password-hash").build();
        entityManager.persist(user);
        return user;
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
