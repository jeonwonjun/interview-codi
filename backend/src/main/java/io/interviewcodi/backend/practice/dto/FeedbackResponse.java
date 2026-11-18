package io.interviewcodi.backend.practice.dto;

/**
 * @param suggestion      바꿔 볼 한 가지. 충분한 답변이면 null
 * @param suggestionFocus ACCURACY, ANSWER, EVIDENCE, ORDER 중 하나. suggestion이 없으면 null
 * @param previousReview  재답변일 때 직전 시도의 개선점을 보완했는지
 */
public record FeedbackResponse(
        long id,
        long answerRevisionId,
        String observation,
        String suggestion,
        String suggestionFocus,
        String previousReview
) {
}
