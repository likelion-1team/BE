package com.babylion.team1.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.babylion.team1.domain.analysis.repository.AnalysisItemRepository;
import com.babylion.team1.domain.analysis.repository.AnalysisRepository;
import com.babylion.team1.domain.conversation.repository.ConversationRepository;
import com.babylion.team1.domain.conversation.repository.ConversationVersionRepository;
import com.babylion.team1.domain.conversation.repository.MessageRepository;
import com.babylion.team1.domain.team.repository.TeamRepository;
import com.babylion.team1.domain.team.repository.TeamSettingsRepository;
import com.babylion.team1.domain.team.repository.TeamTaskTypeRepository;
import com.babylion.team1.domain.upload.dto.request.AnalysisStartRequest;
import com.babylion.team1.domain.upload.dto.request.AnalysisStartRequest.CandidateSelection;
import com.babylion.team1.domain.upload.repository.UploadCandidateRepository;
import com.babylion.team1.domain.upload.repository.UploadRepository;
import com.babylion.team1.domain.user.entity.User;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalysisStartServiceTest {

    private static final String KEY = "6f1c2a7e-0d1b-4c5e-9a2f-1b3c4d5e6f70";
    private static final CandidateSelection SELECTION = new CandidateSelection("sel_01", 5L);

    private final EntityManager entityManager = mock(EntityManager.class);
    private final UploadRepository uploads = mock(UploadRepository.class);
    private final AnalysisRepository analyses = mock(AnalysisRepository.class);
    private final AnalysisStartService service = new AnalysisStartService(entityManager, uploads,
            mock(UploadCandidateRepository.class), mock(TeamRepository.class), mock(TeamTaskTypeRepository.class),
            mock(TeamSettingsRepository.class), analyses, mock(AnalysisItemRepository.class),
            mock(ConversationRepository.class), mock(ConversationVersionRepository.class),
            mock(MessageRepository.class), mock(UploadFileStorage.class), mock(ChatGptExportReader.class),
            transactionManager());

    private static org.springframework.transaction.PlatformTransactionManager transactionManager() {
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(ignored -> new org.springframework.transaction.support.SimpleTransactionStatus());
        return manager;
    }

    @Test
    void selectionOrderDoesNotChangeIdempotencyHash() {
        var second = new CandidateSelection("sel_02", 7L);
        var firstOrder = new AnalysisStartRequest(1, List.of(SELECTION, second));
        var secondOrder = new AnalysisStartRequest(1, List.of(second, SELECTION));
        org.assertj.core.api.Assertions.assertThat(AnalysisStartService.hashRequest(10L, firstOrder))
                .isEqualTo(AnalysisStartService.hashRequest(10L, secondOrder));
    }

    @Test
    void rejectsMissingKeyAndEmptyOrDuplicateSelectionsBeforeWriting() {
        assertError(ErrorCode.VALIDATION_FAILED,
                () -> service.start(10L, 4L, null, new AnalysisStartRequest(1, List.of(SELECTION))));
        assertError(ErrorCode.VALIDATION_FAILED,
                () -> service.start(10L, 4L, KEY, new AnalysisStartRequest(1, List.of())));
        assertError(ErrorCode.VALIDATION_FAILED,
                () -> service.start(10L, 4L, KEY, new AnalysisStartRequest(1, List.of(SELECTION, SELECTION))));
        verifyNoInteractions(uploads, analyses);
    }

    @Test
    void hidesUnknownUpload() {
        when(entityManager.find(User.class, 4L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(mock(User.class));
        when(uploads.findByIdForUpdate(10L)).thenReturn(Optional.empty());
        assertError(ErrorCode.RESOURCE_NOT_FOUND,
                () -> service.start(10L, 4L, KEY, new AnalysisStartRequest(1, List.of(SELECTION))));
    }

    private void assertError(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CustomException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
