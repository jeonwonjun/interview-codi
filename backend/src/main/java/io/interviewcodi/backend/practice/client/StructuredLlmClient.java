package io.interviewcodi.backend.practice.client;

import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * JSON 스키마에 맞는 결과 하나를 돌려주는 LLM 호출. 제공자(OpenAI, Gemini)마다 구현이 있고, 피드백·꼬리질문
 * 생성기는 이 인터페이스만 쓴다. 요청·응답을 제공자 서버에 저장하지 않도록 구현마다 저장을 끈다.
 */
public interface StructuredLlmClient {

    /**
     * @param purpose 로그용 호출 목적
     * @throws AiGenerationException 호출 실패, 응답 미완료·거절, JSON이 아닌 응답
     */
    JsonNode generateJson(String purpose, String instructions, String input, String schemaName,
                          Map<String, Object> schema, int maxOutputTokens);

    /** 결과에 남길 모델 이름. */
    String model();
}
