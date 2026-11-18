package io.interviewcodi.backend.practice.domain;

public enum DiscardReason {
    /** 근거 버전이 교정되어 더 이상 현재 버전이 아니다. */
    SUPERSEDED,
    PRACTICE_DELETED,
    /** 연습을 마쳐서 새 질문을 추가하지 않는다. 피드백에는 쓰지 않는다. */
    PRACTICE_COMPLETED,
    /** 근거 질문이 더 이상 현재 질문이 아니다. */
    QUESTION_CHANGED
}
