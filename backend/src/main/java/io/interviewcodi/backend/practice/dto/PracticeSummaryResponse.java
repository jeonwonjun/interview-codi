package io.interviewcodi.backend.practice.dto;

import java.time.OffsetDateTime;

public record PracticeSummaryResponse(
        long id,
        String startMode,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        int questionCount,
        boolean canProceed
) {
}
