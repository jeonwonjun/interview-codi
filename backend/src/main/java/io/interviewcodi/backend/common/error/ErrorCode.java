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
    ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, "답변을 찾을 수 없습니다."),
    ANSWER_REVISION_NOT_FOUND(HttpStatus.NOT_FOUND, "답변을 찾을 수 없습니다."),
    AI_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다."),
    PRACTICE_COMPLETED(HttpStatus.CONFLICT, "이미 마친 연습입니다. 새 연습을 시작해 주세요."),
    NOT_CURRENT_QUESTION(HttpStatus.CONFLICT, "지금 진행 중인 질문이 아닙니다."),
    REVISION_CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 먼저 수정된 답변이에요. 최신 내용을 확인한 뒤 다시 수정해 주세요."),
    NO_MORE_QUESTIONS(HttpStatus.CONFLICT, "준비한 질문을 모두 연습했어요. 연습을 마치거나 지금 질문에 다시 답해 보세요."),
    NOT_LATEST_REVISION(HttpStatus.CONFLICT, "수정된 답변이 있어요. 최신 답변으로 다시 시도해 주세요."),

    INVALID_RECORDING(HttpStatus.BAD_REQUEST, "녹음 파일을 읽을 수 없어요. 다시 녹음하거나 글로 답해 주세요."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일이 너무 커요. 녹음은 3분 안으로 해 주세요."),
    RECORDING_NOT_FOUND(HttpStatus.NOT_FOUND, "녹음을 찾을 수 없습니다."),
    TRANSCRIPTION_IN_PROGRESS(HttpStatus.CONFLICT, "음성을 글로 옮기는 중이에요. 끝난 뒤에 다시 시도해 주세요.");

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
