package io.interviewcodi.backend.practice.dto;

/**
 * 답변 시도 하나. 피드백과 요청 상태는 현재 버전의 것만 담는다. 교정 전 버전의 피드백은 넣지 않는다.
 *
 * @param feedback        현재 버전의 피드백. 아직 없으면 null
 * @param feedbackRequest 현재 버전의 가장 최근 피드백 요청. 없으면 null
 */
public record AnswerAttemptResponse(
        long id,
        long questionId,
        int attemptNo,
        AnswerRevisionResponse currentRevision,
        FeedbackResponse feedback,
        AiRequestResponse feedbackRequest
) {
}
