package io.interviewcodi.backend.practice.client;

/**
 * LLM 호출 한 번의 측정값. 프롬프트·답변 본문은 담지 않는다.
 *
 * @param purpose 호출 목적(feedback, follow-up)
 * @param outputTokens  출력 토큰. OpenAI는 생각(추론) 토큰을 포함하고, Gemini는 따로 센다
 * @param thoughtTokens 생각(추론) 토큰
 * @param status        completed 또는 오류 종류
 */
public record LlmCall(String model, String purpose, long latencyMillis, int inputTokens, int outputTokens,
                      int thoughtTokens, String status) {
}
