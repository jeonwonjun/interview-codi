package io.interviewcodi.backend.practice.client;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** LLM으로 피드백을 만든다. 프롬프트는 {@code prompts/feedback-v1.md}. */
public class LlmFeedbackGenerator implements FeedbackGenerator {

    static final String PROMPT = "feedback-v1";
    private static final Set<String> FOCUSES = Set.of("ACCURACY", "ANSWER", "EVIDENCE", "ORDER");
    private static final int MAX_OUTPUT_TOKENS = 2_048;
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "observation", Map.of("type", "string", "description", "답변에서 본 부분과 잘된 점, 2~3문장"),
                    "suggestion", nullableString("다음 답변에서 바꿔 볼 한 가지. 충분하면 null"),
                    "suggestionFocus", Map.of("type", List.of("string", "null"),
                            "enum", Arrays.asList("ACCURACY", "ANSWER", "EVIDENCE", "ORDER", null),
                            "description", "suggestion을 고른 기준. suggestion이 null이면 null"),
                    "previousReview", nullableString("직전 개선점을 보완했는지. 주어지지 않았으면 null")),
            "required", List.of("observation", "suggestion", "suggestionFocus", "previousReview"),
            "additionalProperties", false);

    private final StructuredLlmClient client;
    private final String systemInstruction = PromptLoader.load(PROMPT);
    private final String generatorName;

    public LlmFeedbackGenerator(StructuredLlmClient client) {
        this.client = client;
        this.generatorName = client.model() + "/" + PROMPT;
    }

    @Override
    public GeneratedFeedback generate(FeedbackInput input) {
        StringBuilder prompt = new StringBuilder()
                .append("질문: ").append(input.question()).append("\n\n")
                .append("답변: ").append(input.answer()).append('\n');
        if (input.previousSuggestion() != null) {
            prompt.append("\n직전 시도의 개선점: ").append(input.previousSuggestion()).append('\n');
        }
        JsonNode json = client.generateJson("feedback", systemInstruction, prompt.toString(), "feedback", SCHEMA,
                MAX_OUTPUT_TOKENS);
        return toFeedback(json, input.previousSuggestion() != null);
    }

    private GeneratedFeedback toFeedback(JsonNode json, boolean reanswer) {
        String observation = text(json, "observation");
        String suggestion = text(json, "suggestion");
        String focus = text(json, "suggestionFocus");
        String previousReview = reanswer ? text(json, "previousReview") : null;
        // 스키마가 막지 못하는 조합(개선점과 기준이 하나만 있음)은 DB 제약과 같은 기준으로 거른다.
        boolean consistent = (suggestion == null) == (focus == null) && (focus == null || FOCUSES.contains(focus));
        if (observation == null || !consistent) {
            throw new AiGenerationException("INVALID_OUTPUT", null);
        }
        return new GeneratedFeedback(observation, suggestion, focus, previousReview, generatorName);
    }

    private static Map<String, Object> nullableString(String description) {
        return Map.of("type", List.of("string", "null"), "description", description);
    }

    private static String text(JsonNode json, String field) {
        JsonNode node = json.path(field);
        if (node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asString("").strip();
        return value.isEmpty() ? null : value;
    }
}
