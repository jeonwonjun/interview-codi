package io.interviewcodi.backend.practice.dto;

/**
 * @param baseRevisionNo 화면이 보고 있던 현재 버전 번호. 그사이 다른 교정이 저장됐으면 409
 */
public record ReviseAnswerRequest(Integer baseRevisionNo, String content) {
}
