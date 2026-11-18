package io.interviewcodi.backend.practice.client;

/**
 * 답변 피드백을 만드는 외부 생성기. 실제 AI 제공자는 이 인터페이스로 연결한다.
 * 워커가 트랜잭션 밖에서 호출하므로, 구현은 DB를 만지지 않고 입력만 보고 결과를 돌려준다.
 */
public interface FeedbackGenerator {

    /**
     * @throws AiGenerationException 생성에 실패했을 때. 일시적인 실패면 워커가 다시 시도한다.
     */
    GeneratedFeedback generate(FeedbackInput input);

    /**
     * @param previousSuggestion 재답변이면 직전 시도의 피드백이 제안한 개선점. 아니면 null
     */
    record FeedbackInput(String question, String answer, String previousSuggestion) {
    }

    /**
     * @param suggestion      바꿔 볼 한 가지. 충분한 답변이면 null
     * @param suggestionFocus suggestion이 있을 때 ACCURACY, ANSWER, EVIDENCE, ORDER 중 하나
     * @param previousReview  재답변일 때 직전 개선점을 보완했는지. 아니면 null
     * @param generator       생성기·모델 이름(품질 비교용)
     */
    record GeneratedFeedback(String observation, String suggestion, String suggestionFocus, String previousReview,
                             String generator) {
    }
}
