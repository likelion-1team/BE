package com.babylion.team1.domain.dashboard.dto;

/** 지표 상태. 여러 상태에 해당하면 먼저 선언한 상태를 적용한다. */
public enum MetricStatus {

    /** TEAM에서 유효 기여자가 부족하거나, 다른 숨긴 지표를 역산할 수 있어 숨김 */
    SUPPRESSED,
    /** 서로 다른 기준 버전이나 토큰 출처가 섞임 */
    NOT_COMPARABLE,
    /** 대상 0개 */
    NO_DATA,
    /** 토큰 등 필요한 값이 빠짐 */
    NOT_MEASURED,
    /** 대상은 있지만 분모가 0 */
    ZERO_DENOMINATOR,
    /** 표본 부족 */
    INSUFFICIENT_SAMPLE,
    /** 결과 확인률 부족(토큰 지표만) */
    INSUFFICIENT_CONFIRMATION,
    AVAILABLE
}
