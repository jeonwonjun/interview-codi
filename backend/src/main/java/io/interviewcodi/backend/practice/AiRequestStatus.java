package io.interviewcodi.backend.practice;

enum AiRequestStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    /** 결과가 더 이상 유효하지 않아 버린 요청. 사유는 {@link DiscardReason}. */
    DISCARDED
}
