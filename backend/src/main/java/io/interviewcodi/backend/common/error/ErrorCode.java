package io.interviewcodi.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 오류 응답의 {@code code}. 프론트엔드는 이 값으로 분기하므로 이름을 바꾸지 않는다.
 */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN_ORIGIN(HttpStatus.FORBIDDEN, "허용하지 않은 출처의 요청입니다."),

    UNSUPPORTED_START_MODE(HttpStatus.BAD_REQUEST, "지원하지 않는 연습 시작 방식입니다."),
    INVALID_ANSWER_CONTENT(HttpStatus.BAD_REQUEST, "답변은 공백을 제외하고 1자 이상 3,000자 이하로 입력해 주세요."),
    // 다른 사용자의 자료도 존재 여부를 드러내지 않도록 같은 404로 응답한다.
    PRACTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "연습을 찾을 수 없습니다."),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "질문을 찾을 수 없습니다."),
    PRACTICE_COMPLETED(HttpStatus.CONFLICT, "이미 마친 연습입니다. 새 연습을 시작해 주세요."),
    NOT_CURRENT_QUESTION(HttpStatus.CONFLICT, "지금 진행 중인 질문이 아닙니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
