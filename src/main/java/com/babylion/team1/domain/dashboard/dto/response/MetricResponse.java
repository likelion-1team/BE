package com.babylion.team1.domain.dashboard.dto.response;

import com.babylion.team1.domain.dashboard.dto.MetricCode;
import com.babylion.team1.domain.dashboard.dto.MetricStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * 지표 하나. value는 status가 AVAILABLE일 때만 값이 있고, 비율은 0~1이다.
 * sampleSize: 이 지표에 쓰인 대화 수.
 * missingCount: 비율 지표는 아직 확인하지 않은 대화 수, 토큰 지표는 토큰을 측정하지 못한 대화 수.
 * SUPPRESSED면 value와 모든 건수가 null이다.
 */
public record MetricResponse(
        MetricCode code,
        BigDecimal value,
        MetricCode.Unit unit,
        Long numerator,
        Long denominator,
        Long sampleSize,
        Long missingCount,
        String assessmentSource,
        MetricCode.ValueSource valueSource,
        MetricStatus status,
        List<String> reasonCodes
) {

    /** P0의 판정 출처는 직원 확인으로 고정한다 */
    private static final String SELF_REPORT = "SELF_REPORT";

    public static MetricResponse of(MetricCode code, BigDecimal value, Long numerator, Long denominator,
            Long sampleSize, Long missingCount, MetricStatus status) {
        return new MetricResponse(code, value, code.getUnit(), numerator, denominator, sampleSize, missingCount,
                SELF_REPORT, code.getValueSource(), status, List.of());
    }

    /** value와 모든 건수를 감춘 지표 */
    public static MetricResponse hidden(MetricCode code, MetricStatus status) {
        return of(code, null, null, null, null, null, status);
    }
}
