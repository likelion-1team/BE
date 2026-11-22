package com.babylion.team1.domain.upload.service;

import static com.babylion.team1.domain.conversation.entity.MessageRole.ASSISTANT;
import static com.babylion.team1.domain.conversation.entity.MessageRole.USER;
import static org.assertj.core.api.Assertions.assertThat;

import com.babylion.team1.domain.upload.service.ChatGptConversationBranch.Turn;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationMetricsTest {

    private final ReworkJudge judge = new ReworkJudge(List.of("다시", "again"));

    @Test
    void firstQuestionIsExcludedAndOnlyTheThreeMostRecentQuestionsAreCompared() {
        assertThat(judge.isRework("다시 설명해", true, List.of())).isFalse();
        assertThat(judge.isRework("보고서 요약", true, List.of("처음 질문"))).isTrue();
        assertThat(judge.isRework("AGAIN please", false, List.of("처음 질문"))).isTrue();
        assertThat(judge.isRework("abcdefghij", false, List.of("abcdefghik", "다른 내용", "새 작업"))).isTrue();
        assertThat(judge.isRework("abcdefghij", false, List.of("abcdefghij", "첫째", "둘째", "셋째"))).isFalse();
        assertThat(SuccessJudge.isSuccessful(10, false)).isTrue();
        assertThat(SuccessJudge.isSuccessful(11, false)).isFalse();
        assertThat(SuccessJudge.isSuccessful(3, true)).isFalse();
    }

    @Test
    void calculatesCumulativeInputAndCountsOnlyHumanGapsOfAtMostTenMinutes() {
        var metrics = new ConversationMetrics(judge);
        var time = LocalDateTime.of(2026, 11, 21, 10, 0);
        var result = metrics.calculate(List.of(
                new Turn(USER, "hello", time, null, false),
                new Turn(ASSISTANT, "hello", time.plusSeconds(1), "gpt-4o", false),
                new Turn(USER, "hello", time.plusSeconds(601), null, false),
                new Turn(ASSISTANT, "hello", time.plusSeconds(610), "gpt-4o", false),
                new Turn(USER, "world", time.plusSeconds(1_211), null, false)));
        assertThat(result.requests()).extracting(ConversationMetrics.Request::input).containsExactly(1L, 3L, 5L);
        assertThat(result.input()).isEqualTo(9);
        assertThat(result.output()).isEqualTo(2);
        assertThat(result.rework()).isEqualTo(600);
        assertThat(result.review()).isZero();
        assertThat(result.success()).isTrue();
    }

    @Test
    void identifiesAllThreeFeedbackTypesAndComputesSavingsInCode() {
        var pricing = new AnalysisPricing();
        pricing.setUsdKrwRate(new BigDecimal("1400"));
        pricing.setLowTierModel("mini");
        pricing.setPricing(Map.of(
                "large", new AnalysisPricing.Rate(new BigDecimal("2.5"), BigDecimal.TEN, "high"),
                "mini", new AnalysisPricing.Rate(new BigDecimal("0.15"), new BigDecimal("0.60"), "low")));
        var detector = new AnalysisFeedbackRules(pricing);
        var earlyRework = new ConversationMetrics.Result(List.of(
                new ConversationMetrics.Request(0, 1, 2, 1, false),
                new ConversationMetrics.Request(2, 10, 3, 7, true),
                new ConversationMetrics.Request(4, 20, 4, 7, true)), "large", 0, 0, false);
        var finding = detector.detect(earlyRework).get(0);
        assertThat(finding.type()).isEqualTo(AnalysisFeedbackRules.Type.MISSING_CONTEXT);
        assertThat(finding.tokens()).isEqualTo(37);
        assertThat(finding.turns()).isEqualTo(2);
        assertThat(finding.evidence()).containsExactly(2, 4);

        var longRequests = new ArrayList<ConversationMetrics.Request>();
        for (int i = 0; i < 15; i++) {
            longRequests.add(new ConversationMetrics.Request(i * 2, 100L * (i + 1), 1, 100, false));
        }
        var longFinding = detector.detect(new ConversationMetrics.Result(longRequests, "large", 0, 0, false)).get(0);
        assertThat(longFinding.type()).isEqualTo(AnalysisFeedbackRules.Type.LONG_CONVERSATION);
        assertThat(longFinding.tokens()).isEqualTo(2500);

        var small = new ConversationMetrics.Result(earlyRework.requests().subList(0, 1), "large", 0, 0, true);
        var modelFinding = detector.detect(small).get(0);
        assertThat(modelFinding.type()).isEqualTo(AnalysisFeedbackRules.Type.OVERSPEC_MODEL);
        assertThat(modelFinding.cost()).isPositive();
        assertThat(pricing.cost(pricing.rate("unknown"), 1, 2)).isNull();
    }
}
