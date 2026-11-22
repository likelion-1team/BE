package com.babylion.team1.domain.dashboard.dto;

import java.time.LocalDate;

/**
 * 대화가 있는 첫날·마지막 날(conversation_date). 대화가 없으면 둘 다 null.
 * DatePeriod와 달리 null을 허용한다.
 */
public record ConversationDateRange(LocalDate first, LocalDate last) {
}
