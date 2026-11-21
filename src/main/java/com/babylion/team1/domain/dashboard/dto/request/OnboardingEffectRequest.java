package com.babylion.team1.domain.dashboard.dto.request;

import java.util.List;

/**
 * 온보딩 전/후 비교 조회 조건. taskTypes는 업무 유형 ID이며 선택 값이다(쉼표로 여러 개).
 * 형식이 잘못되면 바인딩 단계에서 400 INVALID_REQUEST로 응답한다.
 */
public record OnboardingEffectRequest(
        List<Long> taskTypes
) {

    public OnboardingEffectRequest {
        taskTypes = taskTypes != null
                ? taskTypes.stream().filter(id -> id != null).distinct().toList()
                : List.of();
    }
}
