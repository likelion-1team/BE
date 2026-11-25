package com.babylion.team1.domain.dashboard.service;

import com.babylion.team1.domain.dashboard.dto.DatePeriod;
import com.babylion.team1.domain.dashboard.dto.request.DashboardScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 검증을 마친 조회 조건. userId는 ME면 요청자, TEAM이면 null(팀 전체)이다. */
record CohortQuery(
        Long teamId,
        DashboardScope scope,
        Long userId,
        DatePeriod period,
        Long settingsId,
        int settingsVersion
) {

    private static final int COHORT_ID_HEX_LENGTH = 16;

    /** 스냅샷 테이블 없이 조건으로만 계산한다. 같은 조건이면 KPI 카드와 추이에서 같은 값이 나온다. */
    String cohortId() {
        String condition = String.join("|", teamId.toString(), scope.name(), String.valueOf(userId),
                period.from().toString(), period.to().toString(), Integer.toString(settingsVersion));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(condition.getBytes(StandardCharsets.UTF_8));
            return "c_" + HexFormat.of().formatHex(hash).substring(0, COHORT_ID_HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
