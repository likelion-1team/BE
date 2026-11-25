package com.babylion.team1.domain.feedback.service;

import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse;
import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Content;
import com.babylion.team1.domain.feedback.dto.response.SharePreviewResponse.Example;
import com.babylion.team1.domain.feedback.entity.SharePreview;
import com.babylion.team1.domain.feedback.entity.Feedback;
import com.babylion.team1.domain.feedback.entity.FeedbackType;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
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
        var feedback = feedbacks.findByIdForUpdate(feedbackId).filter(row -> row.isOwnedBy(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!evidence.existsByFeedbackId(feedbackId) || evidence.hasDeletedOriginal(feedbackId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        var preview = previews.save(new SharePreview(feedback, publicContent(feedback, evidence.findOriginalTexts(feedbackId)), Instant.now()));
        return new SharePreviewResponse(preview.getId(), preview.getPreviewVersion(),
                preview.getExpiresAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                preview.getRedactionStatus(), preview.getContent());
    }

    private static final Pattern EMAIL = Pattern.compile(
            "[\\p{L}0-9._%+-]+@[\\p{L}0-9.-]+\\.[\\p{L}]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile(
            "(?<![0-9])(?:\\+82[ .-]?|0)(?:[0-9]{1,2})[ .-]?[0-9]{3,4}[ .-]?[0-9]{4}(?![0-9])");
    private static final Pattern LABELED_IDENTIFIER = Pattern.compile(
            "(이름|담당자|고객사|회사명|프로젝트(?:명| ID)?|연락처|name|client|project)"
                    + "\\s*[:=]\\s*(?:[\"‘“'][^\"’”'\\r\\n]+[\"’”']|[^,;\\r\\n]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESIDUAL_IDENTIFIER = Pattern.compile(
            "(?:[\\p{L}0-9._%+-]+@|(?:https?://|www\\.)|\\b[A-Z]{2,}-[0-9]{2,}\\b"
                    + "|(?:이름|담당자|고객사|회사명|프로젝트(?:명| ID)?|연락처)\\s*[:=])");
    private static final String REDACTED = "[비공개]";

    private static Content publicContent(Feedback feedback, List<String> originals) {
        String rawType = feedback.getType();
        String type = Arrays.stream(FeedbackType.values())
                .filter(value -> value.name().equalsIgnoreCase(rawType) || value.getLabel().equals(rawType))
                .map(Enum::name)
                .findFirst().orElse(rawType);
        if (!type.matches("MISSING_CONTEXT|LONG_CONVERSATION|OVERSPEC_MODEL|OUTPUT_FORMAT")) {
            throw new CustomException(ErrorCode.SHARING_REDACTION_FAILED);
        }
        var identifiers = List.of(feedback.getUser().getName(), feedback.getUser().getEmail(),
                feedback.getTeam().getName(), feedback.getTeam().getCreatedBy().getName(),
                feedback.getTeam().getCreatedBy().getEmail());
        return new Content(redact(feedback.getTitle(), identifiers, originals), type,
                redact(feedback.getProblem(), identifiers, originals),
                redact(feedback.getImprovement(), identifiers, originals),
                new Example(redact(feedback.getExampleBefore(), identifiers, originals),
                        redact(feedback.getExampleAfter(), identifiers, originals)));
    }

    private static String redact(String text, List<String> identifiers, List<String> originals) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String redacted = text;
        for (String original : originals) {
            if (original.isBlank()) {
                continue;
            }
            if (original.contains(text.strip())) {
                return "[원문 제외]";
            }
            redacted = redacted.replace(original, "[원문 제외]");
        }
        for (String identifier : identifiers) {
            if (!identifier.isBlank()) {
                redacted = Pattern.compile(Pattern.quote(identifier), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(redacted).replaceAll(REDACTED);
            }
        }
        redacted = LABELED_IDENTIFIER.matcher(redacted).replaceAll(REDACTED);
        redacted = EMAIL.matcher(redacted).replaceAll(REDACTED);
        redacted = PHONE.matcher(redacted).replaceAll(REDACTED);
        if (RESIDUAL_IDENTIFIER.matcher(redacted).find()) {
            throw new CustomException(ErrorCode.SHARING_REDACTION_FAILED);
        }
        return redacted;
    }
}
