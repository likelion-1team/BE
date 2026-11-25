package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.MetricCode;
import com.babylion.team1.domain.dashboard.dto.request.DashboardScope;
import com.babylion.team1.domain.dashboard.dto.request.KpiUnit;
import com.babylion.team1.global.exception.CustomException;
import com.babylion.team1.global.exception.ErrorCode;
import com.babylion.team1.global.response.FieldErrorResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/** 대시보드 v4 요청 값 검증. 잘못된 값은 모두 422 VALIDATION_FAILED로 응답한다. */
final class DashboardParameters {

    private static final int MAX_TREND_DAYS = 366;

    private DashboardParameters() {
    }

    static DashboardScope scope(String value) {
        return parseEnum("scope", value, DashboardScope.class);
    }

    static KpiUnit unit(String value, DashboardScope scope) {
        KpiUnit unit = parseEnum("unit", value, KpiUnit.class);
        if (scope == DashboardScope.TEAM && unit != KpiUnit.WEEK) {
            throw invalid("unit", "팀 지표는 WEEK만 조회할 수 있습니다.");
        }
        return unit;
    }

    static MetricCode metric(String value) {
        return MetricCode.find(required("metric", value))
                .orElseThrow(() -> invalid("metric", "지원하지 않는 값입니다."));
    }

    static int settingsVersion(String value) {
        try {
            return Integer.parseInt(required("settingsVersion", value));
        } catch (NumberFormatException e) {
            throw invalid("settingsVersion", "정수여야 합니다.");
        }
    }

    static DatePeriod personalPeriod(String from, String to) {
        LocalDate fromDate = date("from", from);
        LocalDate toDate = date("to", to);
        if (fromDate.isAfter(toDate)) {
            throw invalid("to", "종료일은 시작일보다 빠를 수 없습니다.");
        }
        return new DatePeriod(fromDate, toDate);
    }

    /** 완료된 KST 주(월~일) 하나 */
    static DatePeriod completedWeek(String weekStart, LocalDate today) {
        LocalDate monday = date("weekStart", weekStart);
        if (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw invalid("weekStart", "월요일이어야 합니다.");
        }
        LocalDate sunday = monday.plusDays(6);
        if (!sunday.isBefore(today)) {
            throw invalid("weekStart", "완료된 주만 조회할 수 있습니다.");
        }
        return new DatePeriod(monday, sunday);
    }

    static void requireTrendPeriod(DatePeriod period) {
        if (period.days() > MAX_TREND_DAYS) {
            throw invalid("to", "조회 기간은 최대 " + MAX_TREND_DAYS + "일입니다.");
        }
    }

    /** 지원하지 않는 파라미터는 무시하지 않고 거부한다 */
    static void requireSupported(Set<String> names, Set<String> supported) {
        List<FieldErrorResponse> errors = names.stream()
                .filter(name -> !supported.contains(name))
                .sorted()
                .map(name -> new FieldErrorResponse(name, "지원하지 않는 파라미터입니다."))
                .toList();
        if (!errors.isEmpty()) {
            throw CustomException.withErrors(ErrorCode.VALIDATION_FAILED, errors);
        }
    }

    static CustomException invalid(String field, String reason) {
        return CustomException.withErrors(ErrorCode.VALIDATION_FAILED, List.of(new FieldErrorResponse(field, reason)));
    }

    private static LocalDate date(String field, String value) {
        String text = required(field, value);
        if (text.length() != 10 || text.startsWith("0000-")) {
            throw invalid(field, "YYYY-MM-DD 형식의 날짜여야 합니다.");
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw invalid(field, "YYYY-MM-DD 형식의 날짜여야 합니다.");
        }
    }

    private static <E extends Enum<E>> E parseEnum(String field, String value, Class<E> type) {
        String name = required(field, value);
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw invalid(field, "지원하지 않는 값입니다.");
        }
    }

    private static String required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "필수 값입니다.");
        }
        return value;
    }
}
