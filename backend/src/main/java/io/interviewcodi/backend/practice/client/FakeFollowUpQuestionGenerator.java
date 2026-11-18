package io.interviewcodi.backend.practice.client;

import org.springframework.stereotype.Component;

/**
 * 실제 AI 제공자를 연결하기 전까지 쓰는 개발용 꼬리질문 생성기. 답변 내용과 상관없이 정해진 질문을 돌려준다.
 */
@Component
class FakeFollowUpQuestionGenerator implements FollowUpQuestionGenerator {

    private final FakeLatency latency;

    FakeFollowUpQuestionGenerator(FakeLatency latency) {
        this.latency = latency;
    }

    @Override
    public GeneratedQuestion generate(FollowUpInput input) {
        latency.simulate();
        return new GeneratedQuestion(
                "(개발용 꼬리질문) 방금 말씀하신 내용에서 본인이 직접 결정한 부분은 무엇이었나요?",
                FakeFeedbackGenerator.NAME);
    }
}
