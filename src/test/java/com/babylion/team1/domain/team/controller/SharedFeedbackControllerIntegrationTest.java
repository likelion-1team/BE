package com.babylion.team1.domain.team.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.babylion.team1.domain.conversation.entity.Conversation;
import com.babylion.team1.domain.conversation.entity.ConversationVersion;
import com.babylion.team1.domain.conversation.entity.Message;
import com.babylion.team1.domain.conversation.entity.MessageRole;
import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.entity.FeedbackEvidence;
import com.babylion.team1.domain.feedback.entity.FeedbackReaction;
import com.babylion.team1.domain.feedback.entity.ReactionType;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamMember;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.support.TestAccessTokens;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SharedFeedbackControllerIntegrationTest {

    private static final String REACTION = "/api/v1/feedbacks/{feedbackId}/reaction";
    private static final String SHARE = "/api/v1/feedbacks/{feedbackId}/share";
    private static final String SHARED_CASES = "/api/v1/teams/{teamId}/shared-cases";
    private static final String MY_FEEDBACKS = "/api/v1/teams/{teamId}/feedbacks";
    private static final String GUIDES = "/api/v1/teams/{teamId}/guides";
    private static final String MISSING_CONTEXT = "MISSING_CONTEXT";
    private static final String MISSING_CONTEXT_LABEL = "맥락 누락";
    private static final String LONG_CONVERSATION = "LONG_CONVERSATION";
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TestAccessTokens accessTokens;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void sharePreviewStoresOnlyPublicTemplatesAndHidesOtherOwners() throws Exception {
        TeamFixture fixture = saveTeam();
        Feedback feedback = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT,
                "공유할 작업 요약", "private@example.com 010-1234-5678 형식은 표로 작성");
        Long conversationId = addPrivateEvidence(feedback);
        entityManager.flush();
        String path = "/api/v1/feedbacks/{feedbackId}/share-preview";
        var post = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path, feedback.getId());
        String response = mockMvc.perform(post
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(fixture.member().getId()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("S201"))
                .andExpect(jsonPath("$.data.previewVersion").value(1))
                .andExpect(jsonPath("$.data.redactionStatus").value("PASSED"))
                .andExpect(jsonPath("$.data.content.type").value(MISSING_CONTEXT))
                .andExpect(jsonPath("$.data.content.example.before").value("개선 전 예시"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(response).contains("형식은 표로 작성").doesNotContain( "private@example.com", "010-1234-5678", "보호된 원문",
                "feedbackId", "conversationId");
        assertThat(jdbc.queryForObject("""
                select timestampdiff(minute, created_at, expires_at)
                from share_previews where feedback_id = ?
                """, Integer.class, feedback.getId())).isEqualTo(30);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path, feedback.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(fixture.leader().getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        var conversation = entityManager.find(Conversation.class, conversationId);
        var deleted = saveConversationVersion(conversation, 1, "보호된 원문");
        conversation.promote(deleted);
        entityManager.flush();
        jdbc.update("update conversation_versions set deleted_at = current_timestamp(6) where id = ?", deleted.getId());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path, feedback.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(fixture.member().getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        var unsupported = saveFeedback(fixture.taskType(), fixture.member(), "PRIVATE_CUSTOM", "비공개 유형", null);
        addPrivateEvidence(unsupported);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path, unsupported.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(fixture.member().getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SHARING_REDACTION_FAILED"));
        assertThat(jdbc.queryForObject("select count(*) from share_previews where feedback_id = ?",
                Integer.class, unsupported.getId())).isZero();
    }

    @Test
    void feedbackDetailReturnsEvidenceMetadataOnlyToOwner() throws Exception {
        String path = "/api/v1/feedbacks/{feedbackId}";
        TeamFixture fixture = saveTeam();
        Feedback feedback = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "피드백 상세", null);
        feedback.share();
        Long conversationId = addPrivateEvidence(feedback);
        entityManager.flush();
        entityManager.clear();

        String body = request(path, feedback.getId(), fixture.member())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.data.feedbackId").value(feedback.getId()))
                .andExpect(jsonPath("$.data.type").value(MISSING_CONTEXT))
                .andExpect(jsonPath("$.data.title").value("피드백 상세"))
                .andExpect(jsonPath("$.data.problem").value("문제 요약"))
                .andExpect(jsonPath("$.data.causes[0]").value("비공개 원인"))
                .andExpect(jsonPath("$.data.improvement").value("개선 방안"))
                .andExpect(jsonPath("$.data.example.before").value("개선 전 예시"))
                .andExpect(jsonPath("$.data.example.after").value(nullValue()))
                .andExpect(jsonPath("$.data.expectedSaving.status").value("NOT_MEASURED"))
                .andExpect(jsonPath("$.data.conversationId").value(conversationId))
                .andExpect(jsonPath("$.data.evidenceMessageIds[0]").value("m_0"))
                .andExpect(jsonPath("$.data.sharing.version").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("보호된 원문");

        request(path, feedback.getId(), fixture.leader())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        request(path, Long.MAX_VALUE, fixture.member())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get(path, feedback.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conversationDetailReturnsOrderedMessagesAndOnlyMatchingEvidenceToOwner() throws Exception {
        String path = "/api/v1/conversations/{conversationId}";
        TeamFixture fixture = saveTeam();
        Feedback feedback = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "근거 피드백", null);
        feedback.share();
        Long conversationId = addPrivateEvidence(feedback);
        Conversation conversation = entityManager.find(Conversation.class, conversationId);
        entityManager.persist(Message.builder().conversation(conversation).role(MessageRole.ASSISTANT)
                .idx(2).content("수정한 응답").build());
        entityManager.persist(Message.builder().conversation(conversation).role(MessageRole.USER)
                .idx(1).content("다시 작성해 줘").isRework(true).build());
        Feedback unrelated = saveFeedback(fixture.taskType(), fixture.member(), LONG_CONVERSATION, "다른 대화", null);
        addPrivateEvidence(unrelated);
        entityManager.flush();
        entityManager.clear();

        request(path, conversationId, fixture.member(), "highlightFeedbackId", feedback.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.data.startedAt").value(DATE + "T12:00:00+09:00"))
                .andExpect(jsonPath("$.data.messages.length()").value(3))
                .andExpect(jsonPath("$.data.messages[0].content").value("보호된 원문"))
                .andExpect(jsonPath("$.data.messages[1].messageId").value("m_1"))
                .andExpect(jsonPath("$.data.messages[1].role").value("USER"))
                .andExpect(jsonPath("$.data.messages[2].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.highlightMessageIds[0]").value("m_0"));
        request(path, conversationId, fixture.member())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.highlightMessageIds").isEmpty());
        request(path, conversationId, fixture.member(), "highlightFeedbackId", unrelated.getId().toString())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        request(path, conversationId, fixture.leader())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        request(path, Long.MAX_VALUE, fixture.member())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get(path, conversationId)).andExpect(status().isUnauthorized());
    }

    @Test
    void conversationDetailSwitchesVersionsWithoutMixingMessages() throws Exception {
        String path = "/api/v1/conversations/{conversationId}";
        TeamFixture fixture = saveTeam();
        Feedback feedback = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "버전 테스트", null);
        Long conversationId = addPrivateEvidence(feedback);
        Conversation conversation = entityManager.find(Conversation.class, conversationId);
        var first = saveConversationVersion(conversation, 1, "첫 번째 원문");
        var second = saveConversationVersion(conversation, 2, "재업로드 원문");
        conversation.promote(first);
        entityManager.flush();
        entityManager.clear();

        request(path, conversationId, fixture.member())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.messages[0].content").value("첫 번째 원문"));

        entityManager.find(Conversation.class, conversationId)
                .promote(entityManager.find(ConversationVersion.class, second.getId()));
        entityManager.flush();
        entityManager.clear();

        request(path, conversationId, fixture.member())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.messages[0].content").value("재업로드 원문"));
    }

    private ConversationVersion saveConversationVersion(Conversation conversation, int version, String content) {
        var saved = ConversationVersion.builder().conversation(conversation).sourceUpload(conversation.getUpload())
                .inputVersion(version).contentHash(Integer.toString(version).repeat(64))
                .startedAt(Instant.parse("2026-11-20T03:00:00Z")).conversationDate(DATE).turnCount(1).build();
        entityManager.persist(saved);
        entityManager.persist(Message.builder().conversation(conversation).version(saved).role(MessageRole.USER)
                .idx(0).content(content).build());
        return saved;
    }

    @Test
    void sharedCasesReturnOnlyPublicFieldsAndSortTiesById() throws Exception {
        TeamFixture fixture = saveTeam();
        TeamFixture other = saveTeam();
        Feedback first = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "공유된 가이드", "개선 후 예시");
        Feedback second = saveFeedback(fixture.taskType(), fixture.member(), LONG_CONVERSATION, "공유된 사례", null);
        saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "비공개 사례", "비공개 개선 예시");
        Feedback otherShared = saveFeedback(other.taskType(), other.member(), MISSING_CONTEXT, "다른 팀 사례", null);
        publishGuide(first);
        second.share();
        otherShared.share();
        addPrivateEvidence(first);
        entityManager.flush();
        jdbc.update("update feedbacks set shared_at = ? where id in (?, ?)",
                DATE.atTime(12, 0), first.getId(), second.getId());
        entityManager.clear();

        String body = request(SHARED_CASES, fixture.team().getId(), fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("조회 성공"))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].feedbackId").value(second.getId()))
                .andExpect(jsonPath("$.data.items[0].type").value("긴 대화"))
                .andExpect(jsonPath("$.data.items[0].example.after").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].isGuide").value(false))
                .andExpect(jsonPath("$.data.items[1].feedbackId").value(first.getId()))
                .andExpect(jsonPath("$.data.items[1].taskType").value(fixture.taskType().getName()))
                .andExpect(jsonPath("$.data.items[1].type").value(MISSING_CONTEXT_LABEL))
                .andExpect(jsonPath("$.data.items[1].example.before").value("개선 전 예시"))
                .andExpect(jsonPath("$.data.items[1].example.after").value("개선 후 예시"))
                .andExpect(jsonPath("$.data.items[1].isGuide").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Map<String, Object> publicItem = JsonPath.read(body, "$.data.items[1]");
        assertThat(publicItem).containsOnlyKeys("feedbackId", "taskType", "type", "title", "problem",
                "improvement", "example", "isGuide");
        assertThat(body).doesNotContain(fixture.member().getEmail(), "보호된 원문", "비공개 원인");
    }

    @Test
    void sharedCasesFilterByTaskIdAndRecognizeBothTypeRepresentations() throws Exception {
        TeamFixture fixture = saveTeam();
        TeamFixture other = saveTeam();
        TeamTaskType minutes = saveTaskType(fixture.team(), "회의록");
        Feedback coded = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "코드 사례", null);
        Feedback legacy = saveFeedback(minutes, fixture.member(), MISSING_CONTEXT_LABEL, "이전 유형명 사례", null);
        Feedback longCase = saveFeedback(minutes, fixture.member(), LONG_CONVERSATION, "긴 대화 사례", null);
        coded.share();
        legacy.share();
        longCase.share();
        Long teamId = fixture.team().getId();

        request(SHARED_CASES, teamId, fixture.leader(), "taskType", minutes.getId().toString())
                .andExpect(jsonPath("$.data.items.length()").value(2));
        request(SHARED_CASES, teamId, fixture.leader(), "type", MISSING_CONTEXT_LABEL)
                .andExpect(jsonPath("$.data.items.length()").value(2));
        request(SHARED_CASES, teamId, fixture.leader(), "type", MISSING_CONTEXT)
                .andExpect(jsonPath("$.data.items.length()").value(2));
        request(SHARED_CASES, teamId, fixture.leader(), "taskType", minutes.getId().toString(),
                "type", MISSING_CONTEXT_LABEL)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].feedbackId").value(legacy.getId()));
        request(SHARED_CASES, teamId, fixture.leader(), "taskType", other.taskType().getId().toString())
                .andExpect(jsonPath("$.data.items").isEmpty());
        request(SHARED_CASES, teamId, fixture.leader(), "type", "존재하지 않는 유형")
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void teamMemberReadsGuidesGroupedByTypeWithKstTimestamps() throws Exception {
        TeamFixture fixture = saveTeam();
        TeamFixture other = saveTeam();
        Feedback older = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "기존 가이드", "개선 후 예시");
        Feedback latest = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT_LABEL, "최신 가이드", null);
        Feedback otherType = saveFeedback(fixture.taskType(), fixture.member(), LONG_CONVERSATION, "긴 대화 가이드", null);
        Feedback sharedOnly = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "공유만 허용", null);
        Feedback foreign = saveFeedback(other.taskType(), other.member(), MISSING_CONTEXT, "타 팀 가이드", null);
        publishGuide(older);
        publishGuide(latest);
        publishGuide(otherType);
        publishGuide(foreign);
        sharedOnly.share();
        entityManager.flush();
        setGuideDate(older, DATE.minusDays(1).atTime(17, 30));
        setGuideDate(latest, DATE.plusDays(1).atTime(17, 30));
        setGuideDate(otherType, DATE.atTime(17, 30));
        entityManager.clear();

        Long teamId = fixture.team().getId();
        String body = request(GUIDES, teamId, fixture.member())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(2))
                .andExpect(jsonPath("$.data.groups[0].type").value(MISSING_CONTEXT_LABEL))
                .andExpect(jsonPath("$.data.groups[0].guides.length()").value(2))
                .andExpect(jsonPath("$.data.groups[0].guides[0].feedbackId").value(latest.getId()))
                .andExpect(jsonPath("$.data.groups[0].guides[1].feedbackId").value(older.getId()))
                .andExpect(jsonPath("$.data.groups[0].guides[0].guideAddedAt").value("2026-11-21T17:30:00+09:00"))
                .andExpect(jsonPath("$.data.groups[0].guides[0].example.before").value("개선 전 예시"))
                .andExpect(jsonPath("$.data.groups[0].guides[0].example.after").value(nullValue()))
                .andExpect(jsonPath("$.data.groups[1].type").value("긴 대화"))
                .andExpect(jsonPath("$.data.groups[1].guides[0].feedbackId").value(otherType.getId()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Map<String, Object> guide = JsonPath.read(body, "$.data.groups[0].guides[0]");
        assertThat(guide).containsOnlyKeys("feedbackId", "title", "problem", "improvement", "example", "guideAddedAt");
        assertThat(body).doesNotContain(fixture.member().getEmail(), "공유만 허용", "타 팀 가이드");
        request(GUIDES, teamId, fixture.leader(), "type", MISSING_CONTEXT)
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].guides.length()").value(2));
        request(GUIDES, teamId, fixture.member(), "type", MISSING_CONTEXT_LABEL)
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].guides.length()").value(2));
        request(GUIDES, teamId, fixture.member(), "type", "존재하지 않는 유형")
                .andExpect(jsonPath("$.data.groups").isEmpty());
    }

    @Test
    void revokingShareRemovesFeedbackFromBothLists() throws Exception {
        TeamFixture fixture = saveTeam();
        Long teamId = fixture.team().getId();
        Feedback feedback = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "공유 사례", "개선 후 예시");
        entityManager.flush();
        entityManager.clear();

        addPrivateEvidence(entityManager.find(Feedback.class, feedback.getId()));
        var previewResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/feedbacks/{feedbackId}/share-preview", feedback.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(fixture.member().getId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number previewId = JsonPath.read(previewResult, "$.data.previewId");
        String approval = """
                {"shared":true,"expectedShareVersion":0,"previewId":%d,"previewVersion":1}
                """.formatted(previewId.longValue());
        share(feedback.getId(), fixture.member(), approval.replace("expectedShareVersion\":0", "expectedShareVersion\":9"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        share(feedback.getId(), fixture.member(), approval)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.message").value("공유 상태 변경 성공"))
                .andExpect(jsonPath("$.data.shared").value(true));
        mockMvc.perform(put("/api/v1/feedbacks/{feedbackId}/guide", feedback.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(fixture.leader().getId())))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                select count(*) from guides g join shared_cases c on c.id = g.shared_case_id
                where c.feedback_id = ? and c.status = 'ACTIVE'
                """, Integer.class, feedback.getId())).isEqualTo(1);
        share(feedback.getId(), fixture.member(), approval)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareVersion").value(1));


        request(SHARED_CASES, teamId, fixture.leader()).andExpect(jsonPath("$.data.items.length()").value(1));
        request(GUIDES, teamId, fixture.member()).andExpect(jsonPath("$.data.groups.length()").value(1));

        share(feedback.getId(), fixture.member(), "{\"shared\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shared").value(false))
                .andExpect(jsonPath("$.data.shareVersion").value(2));
        entityManager.flush();
        entityManager.clear();
        Feedback revoked = entityManager.find(Feedback.class, feedback.getId());
        assertThat(revoked.getIsGuide()).isFalse();
        assertThat(revoked.getSharedAt()).isNull();
        assertThat(jdbc.queryForObject("select status from shared_cases where feedback_id = ?",
                String.class, feedback.getId())).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("""
                select count(*) from guides g join shared_cases c on c.id = g.shared_case_id
                where c.feedback_id = ?
                """, Integer.class, feedback.getId())).isZero();
        assertThat(revoked.getGuideAddedAt()).isNull();
        request(SHARED_CASES, teamId, fixture.leader()).andExpect(jsonPath("$.data.items").isEmpty());
        request(GUIDES, teamId, fixture.member()).andExpect(jsonPath("$.data.groups").isEmpty());
    }

    @Test
    void shareRequiresAuthenticationOwnershipAndExplicitState() throws Exception {
        TeamFixture fixture = saveTeam();
        Feedback feedback = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "공유 사례", null);
        Long feedbackId = feedback.getId();

        mockMvc.perform(patch(SHARE, feedbackId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shared\":true}"))
                .andExpect(status().isUnauthorized());
        share(feedbackId, fixture.leader(), "{\"shared\":true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        share(Long.MAX_VALUE, fixture.member(), "{\"shared\":true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        share(feedbackId, fixture.member(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(feedback.getIsShared()).isFalse();
    }

    @Test
    void restrictsBothEndpointsByTargetTeamAndRequiresAuthentication() throws Exception {
        TeamFixture fixture = saveTeam();
        TeamFixture other = saveTeam();
        Long teamId = fixture.team().getId();

        request(SHARED_CASES, teamId, fixture.member()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEADER_ONLY"));
        request(SHARED_CASES, teamId, other.leader()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
        request(GUIDES, teamId, other.leader()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
        request(SHARED_CASES, Long.MAX_VALUE, fixture.leader()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
        request(GUIDES, Long.MAX_VALUE, fixture.leader()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
        request(SHARED_CASES, teamId, fixture.leader(), "taskType", "invalid")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(get(SHARED_CASES, teamId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(GUIDES, teamId)).andExpect(status().isUnauthorized());
    }

    @Test
    void exposesBothListEndpointsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/shared-cases'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/teams/{teamId}/guides'].get.responses['200']").exists());
    }

    @Test
    void myFeedbacksReturnOnlyOwnedRowsInTheTargetTeamWithTheCallersReaction() throws Exception {
        TeamFixture fixture = saveTeam();
        TeamFixture other = saveTeam();
        Feedback own = saveFeedback(fixture.taskType(), fixture.leader(), MISSING_CONTEXT, "팀장 본인", null);
        Feedback member = saveFeedback(fixture.taskType(), fixture.member(), LONG_CONVERSATION, "팀원 본인", null);
        saveFeedback(other.taskType(), fixture.leader(), MISSING_CONTEXT, "다른 팀 피드백", null);
        addPrivateEvidence(own);
        addPrivateEvidence(member);
        entityManager.persist(FeedbackReaction.builder().feedback(own).user(fixture.leader())
                .reaction(ReactionType.HELPFUL).build());
        entityManager.flush();

        Long teamId = fixture.team().getId();
        request(MY_FEEDBACKS, teamId, fixture.leader())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].feedbackId").value(own.getId()))
                .andExpect(jsonPath("$.data.items[0].expectedSaving.status").value("NOT_MEASURED"))
                .andExpect(jsonPath("$.data.items[0].reaction").value("HELPFUL"));
        request(MY_FEEDBACKS, teamId, fixture.member())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].feedbackId").value(member.getId()))
                .andExpect(jsonPath("$.data.items[0].reaction").value(nullValue()));
        request(MY_FEEDBACKS, teamId, other.leader())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get(MY_FEEDBACKS, teamId)).andExpect(status().isUnauthorized());
    }

    @Test
    void myFeedbacksFilterBeforePagingAndExcludeUnverifiedResults() throws Exception {
        TeamFixture fixture = saveTeam();
        var first = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "근거 있음", null);
        var second = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "다른 근거", null);
        saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "근거 없음", null);
        addPrivateEvidence(first);
        addPrivateEvidence(second);
        entityManager.flush();
        Long teamId = fixture.team().getId();
        User member = fixture.member();

        request(MY_FEEDBACKS, teamId, member, "type", MISSING_CONTEXT, "size", "1", "sort", "FREQUENCY",
                "from", DATE.toString(), "to", DATE.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].occurrenceCount").value(1));
        request(MY_FEEDBACKS, teamId, member, "sort", "SAVING")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void reactionIsReplacedAndCanBeCancelledRepeatedly() throws Exception {
        TeamFixture fixture = saveTeam();
        Feedback feedback = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "반응", null);
        entityManager.flush();
        Long id = feedback.getId();
        String ownerToken = "Bearer " + accessTokens.create(fixture.member().getId());

        recordReaction(id, ownerToken, """
                {"reaction":"NOT_APPLICABLE","reason":"이미 지정했음"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("S200"))
                .andExpect(jsonPath("$.data.reaction").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.data.updatedAt", endsWith("+09:00")));
        assertThat(jdbc.queryForObject("select reason from feedback_reactions where feedback_id = ?",
                String.class, id)).isEqualTo("이미 지정했음");

        recordReaction(id, ownerToken, """
                {"reaction":"HELPFUL"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reaction").value("HELPFUL"));
        assertThat(jdbc.queryForObject("select count(*) from feedback_reactions where feedback_id = ?",
                Long.class, id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select reason from feedback_reactions where feedback_id = ?",
                String.class, id)).isNull();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete(REACTION, id).header(HttpHeaders.AUTHORIZATION, ownerToken))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }
        entityManager.flush();
        assertThat(jdbc.queryForObject("select count(*) from feedback_reactions where feedback_id = ?",
                Long.class, id)).isZero();
    }

    @Test
    void reactionChecksInputAndOwnership() throws Exception {
        TeamFixture fixture = saveTeam();
        Feedback feedback = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "반응 권한", null);
        entityManager.flush();
        Long id = feedback.getId();
        String ownerToken = "Bearer " + accessTokens.create(fixture.member().getId());
        String leaderToken = "Bearer " + accessTokens.create(fixture.leader().getId());
        String validBody = "{\"reaction\":\"HELPFUL\"}";

        for (String body : List.of("{}", "{\"reaction\":\"UNKNOWN\"}",
                "{\"reaction\":\"NOT_APPLICABLE\",\"reason\":\"" + "a".repeat(501) + "\"}")) {
            recordReaction(id, ownerToken, body)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        recordReaction(id, ownerToken, "{\"reaction\":\"HELPFUL\",\"reason\":\"사유\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("사유"));
        recordReaction(id, leaderToken, validBody)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        recordReaction(Long.MAX_VALUE, ownerToken, validBody)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete(REACTION, id).header(HttpHeaders.AUTHORIZATION, leaderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete(REACTION, Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete(REACTION, id)).andExpect(status().isUnauthorized());
    }

    @Test
    void reactionCannotBeCancelledWhenOriginalWasDeleted() throws Exception {
        TeamFixture fixture = saveTeam();
        Feedback feedback = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "반응", null);
        var conversation = entityManager.find(Conversation.class, addPrivateEvidence(feedback));
        var version = saveConversationVersion(conversation, 1, "보호된 원문");
        conversation.promote(version);
        entityManager.flush();
        String ownerToken = "Bearer " + accessTokens.create(fixture.member().getId());
        recordReaction(feedback.getId(), ownerToken, """
                {"reaction":"HELPFUL"}
                """).andExpect(status().isOk());

        jdbc.update("update conversation_versions set deleted_at = current_timestamp(6) where id = ?", version.getId());
        recordReaction(feedback.getId(), ownerToken, "{\"reaction\":\"HELPFUL\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        request("/api/v1/feedbacks/{feedbackId}", feedback.getId(), fixture.member())
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("SOURCE_DELETED"));
        mockMvc.perform(delete(REACTION, feedback.getId()).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(jdbc.queryForObject("select count(*) from feedback_reactions where feedback_id = ?",
                Long.class, feedback.getId())).isEqualTo(1L);
    }

    private ResultActions recordReaction(Long feedbackId, String token, String body) throws Exception {
        return mockMvc.perform(put(REACTION, feedbackId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void recordsUsageOnceAndRejectsConflictingReuses() throws Exception {
        var fixture = saveTeam();
        var feedback = saveFeedback(fixture.taskType(), fixture.member(), MISSING_CONTEXT, "활용 기록", null);
        var other = saveFeedback(fixture.taskType(), fixture.member(), LONG_CONVERSATION, "다른 피드백", null);
        String eventId = java.util.UUID.randomUUID().toString();
        String path = "/api/v1/feedbacks/{feedbackId}/usage-events";
        String body = """
                {"eventId":"%s","eventType":"COPY_SUCCEEDED"}
                """.formatted(eventId);
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path, feedback.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(fixture.member().getId()))
                .contentType("application/json").content(body);

        var first = mockMvc.perform(request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.eventId").value(eventId))
                .andExpect(jsonPath("$.data.eventType").value("COPY_SUCCEEDED"))
                .andReturn().getResponse().getContentAsString();
        var retry = mockMvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(retry).isEqualTo(first);

        mockMvc.perform(request.content(body.replace("COPY_SUCCEEDED", "USE_CONFIRMED")))
                .andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path, other.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(fixture.member().getId()))
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path, feedback.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(fixture.leader().getId()))
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(request.content(body.replace(eventId, "invalid")))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from feedback_usage_events where event_id = ?",
                Integer.class, eventId)).isEqualTo(1);
    }

    private TeamFixture saveTeam() {
        User leader = saveUser("목록 검증 팀장");
        User member = saveUser("목록 검증 팀원");
        Team team = Team.builder().name("공유 목록 검증팀").createdBy(leader).build();
        entityManager.persist(team);
        entityManager.persist(TeamMember.leader(team, leader));
        entityManager.persist(TeamMember.member(team, member));
        return new TeamFixture(team, leader, member, saveTaskType(team, "주간 보고서"));
    }

    private User saveUser(String name) {
        User user = User.builder().name(name).email("shared-feedback-it-" + UUID.randomUUID() + "@example.com")
                .passwordHash("test-password-hash").build();
        entityManager.persist(user);
        return user;
    }

    private TeamTaskType saveTaskType(Team team, String name) {
        TeamTaskType task = TeamTaskType.builder().team(team).name(name).successConditions(List.of("검토 통과"))
                .failureConditions(List.of()).unknownConditions(List.of()).build();
        entityManager.persist(task);
        return task;
    }

    private Feedback saveFeedback(TeamTaskType task, User owner, String type, String title, String exampleAfter) {
        return saveFeedback(task, owner, type, title, exampleAfter, DATE, 1, 300);
    }

    private Feedback saveFeedback(TeamTaskType task, User owner, String type, String title, String exampleAfter,
                                  LocalDate date, int count, int tokens) {
        Upload upload = Upload.builder().team(task.getTeam()).uploader(owner)
                .fileName("conversations.json").conversationCount(1).build();
        upload.startAnalysis(1);
        upload.complete();
        entityManager.persist(upload);
        Feedback feedback = Feedback.builder().team(task.getTeam()).user(owner).taskType(task).upload(upload)
                .feedbackDate(date).type(type).title(title).problem("문제 요약").improvement("개선 방안")
                .causes(List.of("비공개 원인")).exampleBefore("개선 전 예시").exampleAfter(exampleAfter)
                .occurrenceCount(count).expectedSavingTokens(tokens)
                .expectedSavingCost(new BigDecimal("180.50")).expectedSavingTurns(2).build();
        entityManager.persist(feedback);
        return feedback;
    }

    private Long addPrivateEvidence(Feedback feedback) {
        Conversation conversation = Conversation.builder().team(feedback.getTeam()).upload(feedback.getUpload())
                .user(feedback.getUser()).taskType(feedback.getTaskType()).externalId(UUID.randomUUID().toString())
                .conversationDate(DATE).startedAt(DATE.atTime(12, 0)).turnCount(1).estimatedTokens(100).isSuccess(true)
                .build();
        entityManager.persist(conversation);
        entityManager.persist(Message.builder().conversation(conversation).role(MessageRole.USER)
                .idx(0).content("보호된 원문").build());
        entityManager.persist(FeedbackEvidence.builder().feedback(feedback).conversation(conversation)
                .turnIndexes(List.of(0)).build());
        return conversation.getId();
    }

    private void publishGuide(Feedback feedback) {
        feedback.share();
        feedback.addToGuide();
    }

    private void setGuideDate(Feedback feedback, LocalDateTime date) {
        jdbc.update("update feedbacks set guide_added_at = ? where id = ?", date, feedback.getId());
    }

    private ResultActions share(Long feedbackId, User user, String body) throws Exception {
        return mockMvc.perform(patch(SHARE, feedbackId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(user.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions request(String path, Long teamId, User user, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(path, teamId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.create(user.getId()));
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return mockMvc.perform(request);
    }

    private record TeamFixture(Team team, User leader, User member, TeamTaskType taskType) {}
}
