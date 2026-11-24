package com.babylion.team1.global.policy;

import java.math.BigDecimal;

/**
 * 사용자가 바꿀 수 없는 서버 정책값(기능명세서 v4 5.2).
 * 값을 바꾸면 VERSION도 함께 올린다.
 */
public final class ServerPolicy {

    public static final int VERSION = 1;
    /** 비율 지표의 최소 분모 */
    public static final int MIN_SAMPLE_SIZE = 20;
    /** 팀 지표를 공개하는 최소 기여 인원 */
    public static final int MIN_TEAM_CONTRIBUTORS = 5;
    /** 성공당 토큰을 공개하는 최소 결과 확인율 */
    public static final BigDecimal MIN_OUTCOME_CONFIRMATION_RATE = new BigDecimal("0.8");
    /** 업로드 파일로 후보 조회와 분석 시작을 할 수 있는 시간(분) */
    public static final int UPLOAD_RETENTION_MINUTES = 60;
    /** 분석 시작 요청당 최대 선택 개수 */
    public static final int MAX_SELECTIONS_PER_REQUEST = 100;

    private ServerPolicy() {
    }
}
