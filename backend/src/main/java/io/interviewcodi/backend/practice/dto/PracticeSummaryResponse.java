package io.interviewcodi.backend.practice.dto;

import java.time.OffsetDateTime;

public record PracticeSummaryResponse(
        long id,
        String startMode,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        int questionCount,
        // 답변 시도 수(다시 답하기 포함, 교정은 세지 않음). 달력에서 답한 날만 표시할 때 쓴다.
        int answerCount,
        boolean canProceed
) {
}
