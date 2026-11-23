package com.babylion.team1.domain.dashboard.dto.request;

/**
 * 대시보드 집계 범위.
 * ME: 요청자 본인 대화만 집계(팀장·팀원 모두 가능), TEAM: 팀 전체 집계(팀장만 가능, 팀원이면 403 LEADER_ONLY).
 * 요청에서 생략하면 팀장은 TEAM, 팀원은 ME로 본다.
 */
public enum DashboardScope {
    ME,
    TEAM
}
