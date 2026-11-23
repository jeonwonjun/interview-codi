package io.interviewcodi.backend.practice.dto;

/**
 * 음성 시도의 녹음 정보. 파일은 {@code GET /api/answers/{answerId}/recording}으로만 받는다.
 *
 * @param durationMs 브라우저가 잰 녹음 길이
 */
public record RecordingResponse(long id, int durationMs) {
}
