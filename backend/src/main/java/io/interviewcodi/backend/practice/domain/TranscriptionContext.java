package io.interviewcodi.backend.practice.domain;

/**
 * 음성인식에 넘기는 문맥(OpenAI {@code prompt}). 녹음과 같은 언어(한국어)로 쓴다.
 * <p>
 * 질문 문장은 넣지 않는다. 2026-11-23 비교에서 질문을 넣으면 긴 답변의 오류율은 줄었지만(1.6% → 1.0%),
 * 짧고 머뭇거린 답에 말하지 않은 질문 문구("프로젝트를 하면서")가 끼어들었다. 답변에 없는 말을 피드백이 근거로
 * 쓰게 되므로, 녹음의 성격과 받아 적는 방식만 알린다(결정 기록 0009).
 */
public final class TranscriptionContext {

    public static final String INTERVIEW_ANSWER =
            "개발자 면접 연습에서 지원자가 한국어로 답한 녹음입니다. 머뭇거림과 군말도 들리는 그대로 적습니다.";

    private TranscriptionContext() {
    }
}
