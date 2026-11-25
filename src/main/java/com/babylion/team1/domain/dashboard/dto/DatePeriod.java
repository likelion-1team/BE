package com.babylion.team1.domain.dashboard.dto;

import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 양 끝 날짜를 포함하는 기간 */
public record DatePeriod(LocalDate from, LocalDate to) {

    public DatePeriod {
        if (from == null || to == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "조회 기간을 입력하거나 팀 대상 기간을 먼저 설정해 주세요.");
        }
        if (from.isAfter(to)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}
