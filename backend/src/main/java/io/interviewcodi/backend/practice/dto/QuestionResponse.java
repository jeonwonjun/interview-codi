package io.interviewcodi.backend.practice.dto;

import java.util.List;

/**
 * @param current  연습의 현재 질문인지(가장 마지막 질문)
 * @param attempts 답변 시도. 시도 번호 순서
 */
public record QuestionResponse(
        long id,
        int orderNo,
        String content,
        String sourceKind,
        boolean skipped,
        boolean current,
        List<AnswerAttemptResponse> attempts
) {
}
