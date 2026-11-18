package io.interviewcodi.backend.practice.domain;

public enum QuestionSourceKind {
    /** 자료 없는 연습의 고정 질문. */
    EXPERIENCE_PRESET,
    /** 실제 답변 버전에 근거해 AI가 만든 꼬리질문. */
    FOLLOW_UP
}
