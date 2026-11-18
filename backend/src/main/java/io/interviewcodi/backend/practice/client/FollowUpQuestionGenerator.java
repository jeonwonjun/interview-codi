package io.interviewcodi.backend.practice.client;

import java.util.List;

/**
 * 사용자가 실제로 답한 내용에 근거해 꼬리질문 하나를 만드는 외부 생성기. 워커가 트랜잭션 밖에서 호출한다.
 * 답변에 없는 역할·성과·선택 이유를 사실로 가정하지 않고, 필요한 정보는 질문으로 확인해야 한다.
 */
public interface FollowUpQuestionGenerator {

    /**
     * @throws AiGenerationException 생성에 실패했을 때. 일시적인 실패면 워커가 다시 시도한다.
     */
    GeneratedQuestion generate(FollowUpInput input);

    /**
     * @param askedQuestions 이 연습에서 이미 한 질문들(같은 질문을 되풀이하지 않도록)
     */
    record FollowUpInput(String question, String answer, List<String> askedQuestions) {
    }

    record GeneratedQuestion(String content, String generator) {
    }
}
