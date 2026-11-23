package io.interviewcodi.backend.practice.domain;

public enum AiRequestKind {
    FEEDBACK,
    FOLLOW_UP_QUESTION,
    /** 녹음을 글로 옮긴다. 답변 버전이 아니라 녹음을 입력으로 가진다. */
    TRANSCRIBE
}
