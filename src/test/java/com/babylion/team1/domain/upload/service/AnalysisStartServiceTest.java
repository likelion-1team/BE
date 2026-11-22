package com.babylion.team1.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.babylion.team1.domain.conversation.entity.MessageRole;
import com.babylion.team1.domain.team.entity.Team;
import com.babylion.team1.domain.team.entity.TeamTaskType;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.team.repository.TeamTaskTypeRepository;
import com.babylion.team1.domain.upload.dto.request.AnalysisStartRequest.Selection;
import com.babylion.team1.domain.upload.entity.Upload;
import com.babylion.team1.domain.upload.entity.UploadStatus;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

class AnalysisStartServiceTest {

    @TempDir
    Path directory;

    private final UploadRepository uploads = mock(UploadRepository.class);
    private final TeamTaskTypeRepository taskTypes = mock(TeamTaskTypeRepository.class);
    private final TeamSettingsRepository settings = mock(TeamSettingsRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private UploadFileStorage storage;
    private AnalysisStartService service;
    private Upload upload;

    @BeforeEach
    void setUp() throws Exception {
        Team team = mock(Team.class);
        User user = mock(User.class);
        TeamTaskType taskType = mock(TeamTaskType.class);
        when(team.getId()).thenReturn(3L);
        when(user.getId()).thenReturn(4L);
        when(taskType.getId()).thenReturn(5L);
        upload = Upload.builder().team(team).uploader(user).fileName("conversations.json").conversationCount(1).build();
        ReflectionTestUtils.setField(upload, "uploadedAt", LocalDateTime.now());
        when(uploads.findByIdForUpdate(10L)).thenReturn(Optional.of(upload));
        when(taskTypes.findByTeamIdAndIsActiveTrueOrderById(3L)).thenReturn(List.of(taskType));

        storage = new UploadFileStorage(directory.toString());
        Files.writeString(storage.pathFor(10L), """
                [{"id":"selected","create_time":1795242000,"mapping":{}}]
                """);
        var date = LocalDate.of(2026, 11, 21);
        storage.writeTargetPeriod(10L, new UploadFileStorage.TargetPeriod(date, date));
        service = new AnalysisStartService(uploads, taskTypes, settings, storage, events,
                new ChatGptExportReader(DataSize.ofMegabytes(1)));
    }

    @Test
    void acceptsOwnerSelectionAndRejectsAnotherStartWhileAnalyzing() {
        var selections = List.of(new Selection("selected", 5L));
        var response = service.start(10L, 4L, selections);
        assertThat(response.uploadId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(UploadStatus.ANALYZING);
        assertThat(upload.getStatus()).isEqualTo(UploadStatus.ANALYZING);
        assertThat(upload.getSelectedCount()).isEqualTo(1);
        assertThat(upload.getProgress()).isZero();
        verify(events).publishEvent(any(AnalysisJob.class));
        verifyNoInteractions(settings);

        assertError(ErrorCode.ANALYSIS_IN_PROGRESS, () -> service.start(10L, 4L, selections));
        verifyNoMoreInteractions(events);
    }

    @Test
    void rejectsMissingUploadNonOwnerEmptySelectionAndDisallowedType() {
        var selections = List.of(new Selection("selected", 5L));
        assertError(ErrorCode.UPLOAD_NOT_FOUND, () -> service.start(99L, 4L, selections));
        assertError(ErrorCode.NOT_UPLOADER, () -> service.start(10L, 99L, selections));
        assertError(ErrorCode.NO_CONVERSATION_SELECTED, () -> service.start(10L, 4L, List.of()));
        assertError(ErrorCode.TASK_TYPE_NOT_ALLOWED,
                () -> service.start(10L, 4L, List.of(new Selection("selected", 99L))));
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.start(10L, 4L, List.of(new Selection("outside", 5L))));
        assertThat(upload.getStatus()).isEqualTo(UploadStatus.UPLOADED);
        verifyNoInteractions(events);
    }

    @Test
    void distinguishesACompletedUploadFromAnExpiredOriginal() throws Exception {
        var selections = List.of(new Selection("selected", 5L));
        Files.delete(storage.pathFor(10L));
        assertError(ErrorCode.UPLOAD_EXPIRED, () -> service.start(10L, 4L, selections));
        upload.complete();
        assertError(ErrorCode.ANALYSIS_ALREADY_COMPLETED, () -> service.start(10L, 4L, selections));
        verifyNoInteractions(events);
    }

    @Test
    void onlyRestoresSelectedConversationAndCurrentBranch() throws Exception {
        Files.writeString(storage.pathFor(10L), """
                [
                  {"id":"unselected"},
                  {"id":"selected","current_node":"answer","mapping":{
                    "prompt":{"parent":null,"children":["answer","other"],"message":{
                      "author":{"role":"user"},"content":{"parts":["질문"]},"create_time":1795242000.123456}},
                    "answer":{"parent":"prompt","message":{
                      "author":{"role":"assistant"},"content":{"parts":["선택된 답변"]}}},
                    "other":{"parent":"prompt","message":{
                      "author":{"role":"assistant"},"content":{"parts":["다른 분기"]}}}
                  }}
                ]
                """);
        var reader = new ChatGptExportReader(DataSize.ofMegabytes(1));
        var selected = reader.readSelected(storage.pathFor(10L), false, Set.of("selected"));
        assertThat(selected).containsOnlyKeys("selected");
        var turns = ChatGptConversationBranch.restore(selected.get("selected"));
        assertThat(turns).extracting(ChatGptConversationBranch.Turn::content)
                .containsExactly("질문", "선택된 답변");
        assertThat(turns.get(0).role()).isEqualTo(MessageRole.USER);
        assertThat(turns.get(0).branchPoint()).isTrue();
        assertThat(turns.get(0).createdAt().getNano()).isEqualTo(123_000_000);
    }

    private void assertError(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
