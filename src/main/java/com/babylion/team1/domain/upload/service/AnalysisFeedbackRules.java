package com.babylion.team1.domain.upload.service;

import com.babylion.team1.domain.feedback.entity.FeedbackType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class AnalysisFeedbackRules {

    private final AnalysisPricing pricing;

    List<Finding> detect(ConversationMetrics.Result result) {
        var requests = result.requests();
        var findings = new ArrayList<Finding>();
        var rate = pricing.rate(result.model());
        var earlyRework = requests.stream().limit(3).filter(ConversationMetrics.Request::rework).toList();
        if (earlyRework.size() >= 2) {
            var rework = requests.stream().filter(ConversationMetrics.Request::rework).toList();
            long input = rework.stream().mapToLong(ConversationMetrics.Request::input).sum();
            long output = rework.stream().mapToLong(ConversationMetrics.Request::output).sum();
            findings.add(new Finding(Type.MISSING_CONTEXT, input + output, pricing.cost(rate, input, output),
                    rework.size(), rework.stream().map(ConversationMetrics.Request::index).toList()));
        }
        if (!requests.isEmpty() && (requests.size() >= 15 || requests.get(requests.size() - 1).input() >= 30_000)) {
            long saved = requests.size() > 10
                    ? Math.max(0, requests.get(10).input() - requests.get(10).questionTokens() - 500L)
                        * (requests.size() - 10)
                    : 0;
            int evidenceIndex = requests.get(Math.min(10, requests.size() - 1)).index();
            findings.add(new Finding(Type.LONG_CONVERSATION, saved, pricing.cost(rate, saved, 0), 0,
                    List.of(evidenceIndex)));
        }
        var low = pricing.rate(pricing.getLowTierModel());
        if (rate != null && "high".equals(rate.tier()) && low != null && "low".equals(low.tier())
                && !requests.isEmpty() && requests.size() <= 2 && result.input() + result.output() <= 1_000) {
            var highCost = pricing.cost(rate, result.input(), result.output());
            var lowCost = pricing.cost(low, result.input(), result.output());
            var saving = highCost == null ? null : highCost.subtract(lowCost).max(BigDecimal.ZERO);
            findings.add(new Finding(Type.OVERSPEC_MODEL, 0, saving, 0,
                    requests.stream().map(ConversationMetrics.Request::index).toList()));
        }
        return findings;
    }

    enum Type {
        MISSING_CONTEXT(FeedbackType.MISSING_CONTEXT, "초반에 수정 요청이 반복됐어요.",
                "첫 질문에 목적, 제약과 원하는 결과 형식을 적어보세요.",
                "목표는 [목표]이고 대상은 [대상]입니다. [배경과 제약]을 반영해 [결과 형식]으로 작성해 주세요. "
                        + "필요한 정보가 빠져 있으면 먼저 질문해 주세요."),
        LONG_CONVERSATION(FeedbackType.LONG_CONVERSATION, "대화의 맥락이 길어졌어요.",
                "핵심 내용을 요약한 뒤 새 대화에서 이어가 보세요.",
                "아래 요약을 바탕으로 이어서 작업해 주세요. 목표: [목표], 확정된 조건: [조건], "
                        + "현재 결과: [요약], 남은 작업: [요청]. 결과는 [형식]으로 작성해 주세요."),
        OVERSPEC_MODEL(FeedbackType.OVERSPEC_MODEL, "짧은 작업에 고비용 모델을 사용했어요.",
                "같은 작업을 저비용 모델로 먼저 시도해 보세요.",
                "다음 입력에서 [필요한 정보]만 찾아 [형식]으로 간단히 정리해 주세요. "
                        + "입력에 없는 내용은 추측하지 말고 ‘정보 없음’으로 표시해 주세요. 입력: [원문]");

        final String title;
        final String problem;
        final String improvement;
        final String exampleAfter;

        Type(FeedbackType feedbackType, String problem, String improvement, String exampleAfter) {
            this.title = feedbackType.getLabel();
            this.problem = problem;
            this.improvement = improvement;
            this.exampleAfter = exampleAfter;
        }
    }

    record Finding(Type type, long tokens, BigDecimal cost, int turns, List<Integer> evidence) {
        BigDecimal savingCost() {
            return cost == null ? BigDecimal.ZERO : cost;
        }
    }
}
