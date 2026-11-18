package io.interviewcodi.backend.practice.dto;

/**
 * 폴링용 요청 상태. 끝난 상태(SUCCEEDED·FAILED·DISCARDED)가 되면 화면은 연습 상세를 다시 읽는다.
 *
 * @param discardReason    DISCARDED일 때 사유. 아니면 null
 * @param answerRevisionId 입력으로 쓴 답변 버전. 화면의 현재 버전과 다르면 화면을 바꾸지 않는다
 * @param feedbackId       FEEDBACK이 성공했으면 피드백 ID
 * @param questionId       FOLLOW_UP_QUESTION이 성공했으면 새 질문 ID
 */
public record AiRequestStatusResponse(
        long id,
        String kind,
        String status,
        String discardReason,
        long answerRevisionId,
        Long feedbackId,
        Long questionId
) {
}
