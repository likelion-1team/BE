package com.babylion.team1.domain.deletion.entity;

public enum DeletionJobStatus {
    /** 접근은 이미 막혔고 실제 삭제를 기다리는 상태 */
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
