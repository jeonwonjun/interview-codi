package io.interviewcodi.backend.practice.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 연습 상세. 재접속 복원에도 같은 응답을 쓴다.
 *
 * @param canProceed     답변·교정·꼬리질문 등 진행 요청을 할 수 있는지
 * @param readOnlyReason 진행할 수 없는 이유. 진행할 수 있으면 null
 * @param followUpRequest 처리 중인 꼬리질문 요청. 없으면 null. 재접속한 화면이 폴링을 이어간다
 */
public record PracticeResponse(
        long id,
        String startMode,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        boolean canProceed,
        String readOnlyReason,
        List<QuestionResponse> questions,
        AiRequestResponse followUpRequest
) {
}
