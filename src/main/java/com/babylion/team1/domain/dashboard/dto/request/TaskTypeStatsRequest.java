package com.babylion.team1.domain.dashboard.dto.request;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 교육 필요 업무 조회 조건. 모두 선택 값이다.
 * from·to를 비우면 team_settings의 대상 기간을 쓴다(양 끝 포함, KST).
 * 날짜 형식이 잘못되면 바인딩 단계에서 400 INVALID_REQUEST로 응답한다.
 */
public record TaskTypeStatsRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
) {
}
