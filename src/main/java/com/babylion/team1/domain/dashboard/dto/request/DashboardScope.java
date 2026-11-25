package com.babylion.team1.domain.dashboard.dto.request;

/**
 * 대시보드 집계 범위.
 * ME: 요청자 본인 대화만 집계, TEAM: 팀 전체 집계.
 * 필터 옵션·KPI 카드·추이는 필수이고, 팀장·팀원 모두 ME와 TEAM을 조회할 수 있다.
 * 온보딩 비교·비용 구조는 생략하면 팀장은 TEAM, 팀원은 ME로 보고, 팀원이 TEAM을 요청하면 403 LEADER_ONLY.
 */
public enum DashboardScope {
    ME,
    TEAM
}
