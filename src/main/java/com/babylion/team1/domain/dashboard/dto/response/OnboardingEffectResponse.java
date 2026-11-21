package com.babylion.team1.domain.dashboard.dto.response;

import java.time.LocalDate;

/**
 * 온보딩 전/후 비교.
 * 비교할 수 없으면(온보딩 시작일 또는 대상 기간 미설정) before·after는 null이고 qualityWarning은 false다.
 * qualityWarning: 성공률이 떨어졌는데 성공 업무당 토큰만 줄었으면 true("품질 하락 주의").
 */
public record OnboardingEffectResponse(
        LocalDate onboardingStartDate,
        CoreMetrics before,
        CoreMetrics after,
        boolean qualityWarning
) {

    public static OnboardingEffectResponse notComparable(LocalDate onboardingStartDate) {
        return new OnboardingEffectResponse(onboardingStartDate, null, null, false);
    }

    public static OnboardingEffectResponse of(LocalDate onboardingStartDate, CoreMetrics before, CoreMetrics after) {
        return new OnboardingEffectResponse(onboardingStartDate, before, after, isQualityDrop(before, after));
    }

    /** 한쪽 지표라도 null이면(대화 0건, 성공 0건 등) 판정하지 않고 false */
    private static boolean isQualityDrop(CoreMetrics before, CoreMetrics after) {
        if (before.successRate() == null || after.successRate() == null
                || before.tokensPerSuccess() == null || after.tokensPerSuccess() == null) {
            return false;
        }
        boolean successRateDropped = after.successRate().compareTo(before.successRate()) < 0;
        boolean tokensReduced = after.tokensPerSuccess().compareTo(before.tokensPerSuccess()) < 0;
        return successRateDropped && tokensReduced;
    }
}
