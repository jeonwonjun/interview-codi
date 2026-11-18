package io.interviewcodi.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 오류 응답의 {@code code}. 프론트엔드는 이 값으로 분기하므로 이름을 바꾸지 않는다.
 */
public enum ErrorCode {

    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN_ORIGIN(HttpStatus.FORBIDDEN, "허용하지 않은 출처의 요청입니다.");

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
