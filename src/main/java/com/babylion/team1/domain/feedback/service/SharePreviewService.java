package com.babylion.team1.domain.feedback.service;

import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse;
import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Content;
import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Example;
import com.babylion.team1.domain.feedback.entity.SharePreview;
import com.babylion.team1.domain.feedback.entity.FeedbackType;
import java.util.Arrays;
import com.babylion.team1.domain.feedback.repository.FeedbackEvidenceRepository;
import com.babylion.team1.domain.feedback.repository.FeedbackRepository;
import com.babylion.team1.domain.feedback.repository.SharePreviewRepository;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.Instant;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SharePreviewService {
    private final FeedbackRepository feedbacks;
    private final FeedbackEvidenceRepository evidence;
    private final SharePreviewRepository previews;

    @Transactional
    public SharePreviewResponse create(Long feedbackId, Long userId) {
        var feedback = feedbacks.findById(feedbackId).filter(row -> row.isOwnedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!evidence.existsByFeedbackId(feedbackId) || evidence.hasDeletedOriginal(feedbackId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        var preview = previews.save(new SharePreview(feedback, publicContent(feedback.getType()), Instant.now()));
        return new SharePreviewResponse(preview.getId(), preview.getPreviewVersion(),
                preview.getExpiresAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                preview.getRedactionStatus(), preview.getContent());
    }

    // Only reviewed templates enter the shared snapshot. Private and model-generated text never enters the preview.
    private static Content publicContent(String storedType) {
        String type = Arrays.stream(FeedbackType.values())
                .filter(value -> value.name().equalsIgnoreCase(storedType) || value.getLabel().equals(storedType))
                .map(Enum::name)
                .findFirst().orElse(storedType);
        return switch (type) {
            case "MISSING_CONTEXT" -> new Content("첫 요청에 목적과 제약을 명시하기", type,
                    "초반 요청에 작업 맥락이 충분하지 않았습니다.", "목적, 대상, 제약, 원하는 결과 형식을 먼저 적어 주세요.",
                    new Example("자료를 정리해 줘.", "[목적]에 맞게 [자료]를 정리해 주세요. 조건은 [제약], 출력은 [형식]입니다."));
            case "LONG_CONVERSATION" -> new Content("긴 대화의 핵심 내용을 요약하기", type,
                    "이전 요청과 조건이 쌓이면서 맥락이 길어졌습니다.", "확정된 조건과 남은 작업을 요약해 새 대화에서 이어가세요.",
                    new Example("이어서 작업해 줘.", "목표는 [목표], 확정 조건은 [조건], 남은 작업은 [요청]입니다."));
            case "OVERSPEC_MODEL" -> new Content("업무에 맞는 모델 선택하기", type,
                    "간단한 업무에 고비용 모델을 사용했습니다.", "단순 추출이나 정리는 저비용 모델로 먼저 시도해 보세요.",
                    new Example("내용을 확인해 줘.", "[입력]에서 [필요 항목]만 찾아 [형식]으로 정리해 주세요."));
            case "OUTPUT_FORMAT" -> new Content("보고서에 출력 형식을 명시하기", type,
                    "필요한 항목이 처음 요청에 명시되지 않았습니다.", "필수 항목과 확인할 근거를 요청에 포함합니다.",
                    new Example("보고서 작성해 줘.", "목적, 현황, 근거, 확인 필요 항목으로 작성해 줘."));
            default -> throw new CustomException(ErrorCode.SHARING_REDACTION_FAILED);
        };
    }
}
