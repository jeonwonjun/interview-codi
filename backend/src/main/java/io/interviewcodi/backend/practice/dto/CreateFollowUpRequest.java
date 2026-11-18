package io.interviewcodi.backend.practice.dto;

/**
 * @param answerRevisionId 꼬리질문의 근거로 쓸 답변 버전. 현재 질문에 속한 현재 버전이어야 한다
 */
public record CreateFollowUpRequest(Long answerRevisionId) {
}
