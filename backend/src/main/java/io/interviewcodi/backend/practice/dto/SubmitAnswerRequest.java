package io.interviewcodi.backend.practice.dto;

import java.util.UUID;

/**
 * @param clientRequestId 브라우저가 제출마다 새로 만든 UUID. 같은 제출을 다시 보내면 같은 값을 쓴다.
 */
public record SubmitAnswerRequest(UUID clientRequestId, String content) {
}
