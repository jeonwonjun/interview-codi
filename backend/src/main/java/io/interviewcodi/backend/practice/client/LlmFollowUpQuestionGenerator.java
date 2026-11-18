package io.interviewcodi.backend.practice.client;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** LLM으로 꼬리질문을 만든다. 프롬프트는 {@code prompts/follow-up-v1.md}. */
public class LlmFollowUpQuestionGenerator implements FollowUpQuestionGenerator {

    static final String PROMPT = "follow-up-v1";
    private static final int MAX_OUTPUT_TOKENS = 1_024;
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("question", Map.of("type", "string", "description", "꼬리질문 한 문장")),
            "required", List.of("question"),
            "additionalProperties", false);

    private final StructuredLlmClient client;
    private final String systemInstruction = PromptLoader.load(PROMPT);
    private final String generatorName;

    public LlmFollowUpQuestionGenerator(StructuredLlmClient client) {
        this.client = client;
        this.generatorName = client.model() + "/" + PROMPT;
    }

    @Override
    public GeneratedQuestion generate(FollowUpInput input) {
        StringBuilder prompt = new StringBuilder()
                .append("이미 한 질문:\n");
        input.askedQuestions().forEach(question -> prompt.append("- ").append(question).append('\n'));
        prompt.append("\n방금 답한 질문: ").append(input.question()).append("\n\n")
                .append("답변: ").append(input.answer()).append('\n');
        JsonNode json = client.generateJson("follow-up", systemInstruction, prompt.toString(), "follow_up_question",
                SCHEMA, MAX_OUTPUT_TOKENS);
        String question = json.path("question").asString("").strip();
        if (question.isEmpty()) {
            throw new AiGenerationException("INVALID_OUTPUT", null);
        }
        return new GeneratedQuestion(question, generatorName);
    }
}
