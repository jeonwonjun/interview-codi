package io.interviewcodi.backend.practice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.interviewcodi.backend.practice.client.FeedbackGenerator.FeedbackInput;
import io.interviewcodi.backend.practice.client.FeedbackGenerator.GeneratedFeedback;
import io.interviewcodi.backend.practice.client.FollowUpQuestionGenerator.FollowUpInput;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 제공자 서버 대신 가짜 응답으로, 제공자별 요청(저장 끔·스키마·키 헤더)과 응답 해석·오류 처리를 확인한다.
 * 생성기는 {@link StructuredLlmClient}만 쓰므로 같은 생성기로 두 제공자를 확인한다.
 */
class LlmGeneratorTest {

    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/interactions";

    private MockRestServiceServer gemini;
    private GeminiClient client;
    private final List<LlmCall> calls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        gemini = MockRestServiceServer.bindTo(builder).build();
        GeminiProperties properties = new GeminiProperties("test-key", "gemini-3.8-flash", "low",
                URI.create("https://generativelanguage.googleapis.com"), Duration.ofSeconds(30));
        client = new GeminiClient(builder, properties, calls::add);
    }

    @Test
    @DisplayName("피드백 요청은 저장을 끄고 JSON 스키마와 키 헤더를 보내며, 응답 단계의 텍스트를 피드백으로 바꾼다")
    void generatesFeedback() {
        gemini.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.model").value("gemini-3.8-flash"))
                .andExpect(jsonPath("$.generation_config.thinking_level").value("low"))
                .andExpect(jsonPath("$.response_format.mime_type").value("application/json"))
                .andExpect(jsonPath("$.response_format.schema.required.length()").value(4))
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString("답변: 예약 기능을 맡았습니다.")))
                .andRespond(withSuccess(response("""
                        {"observation":"맡은 기능을 먼저 말했어요.","suggestion":"직접 한 일을 하나 덧붙여 보세요.",
                         "suggestionFocus":"EVIDENCE","previousReview":null}"""), MediaType.APPLICATION_JSON));

        GeneratedFeedback feedback = new LlmFeedbackGenerator(client)
                .generate(new FeedbackInput("맡은 기능을 설명해 주세요.", "예약 기능을 맡았습니다.", null));

        assertThat(feedback.suggestionFocus()).isEqualTo("EVIDENCE");
        assertThat(feedback.previousReview()).isNull();
        assertThat(feedback.generator()).isEqualTo("gemini-3.8-flash/feedback-v1");
        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.inputTokens()).isEqualTo(812);
            assertThat(call.outputTokens()).isEqualTo(120);
            assertThat(call.thoughtTokens()).isEqualTo(300);
            assertThat(call.status()).isEqualTo("completed");
        });
    }

    @Test
    @DisplayName("개선점과 기준 중 하나만 있는 응답은 잘못된 출력으로 실패시킨다")
    void rejectsInconsistentFeedback() {
        gemini.expect(requestTo(URL)).andRespond(withSuccess(response("""
                {"observation":"좋아요.","suggestion":null,"suggestionFocus":"ORDER","previousReview":null}"""),
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new LlmFeedbackGenerator(client)
                .generate(new FeedbackInput("질문", "답변", null)))
                .isInstanceOf(AiGenerationException.class)
                .extracting("errorType").isEqualTo("INVALID_OUTPUT");
    }

    @Test
    @DisplayName("꼬리질문 요청에는 이미 한 질문을 넣고, 응답의 질문 한 문장을 돌려준다")
    void generatesFollowUp() {
        gemini.expect(requestTo(URL))
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString("- 맡은 기능을 설명해 주세요.")))
                .andRespond(withSuccess(response("{\"question\":\"검증은 어디에 두셨나요?\"}"),
                        MediaType.APPLICATION_JSON));

        String question = new LlmFollowUpQuestionGenerator(client)
                .generate(new FollowUpInput("맡은 기능을 설명해 주세요.", "검증을 맡았습니다.", List.of("맡은 기능을 설명해 주세요.")))
                .content();

        assertThat(question).isEqualTo("검증은 어디에 두셨나요?");
    }

    @Test
    @DisplayName("한도 초과·미완료·JSON이 아닌 응답은 종류만 담은 생성 실패로 바꾼다")
    void mapsFailures() {
        gemini.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        gemini.expect(requestTo(URL)).andRespond(withSuccess("""
                {"status":"incomplete","steps":[],"usage":{}}""", MediaType.APPLICATION_JSON));
        gemini.expect(requestTo(URL)).andRespond(withSuccess(response("not json"), MediaType.APPLICATION_JSON));
        LlmFollowUpQuestionGenerator generator = new LlmFollowUpQuestionGenerator(client);
        FollowUpInput input = new FollowUpInput("q", "a", List.of());

        for (String expected : List.of("HTTP_429", "STATUS_INCOMPLETE", "INVALID_OUTPUT")) {
            assertThatThrownBy(() -> generator.generate(input))
                    .isInstanceOf(AiGenerationException.class)
                    .extracting("errorType").isEqualTo(expected);
        }
        assertThat(calls).extracting(LlmCall::status).containsExactly("HTTP_429", "incomplete", "completed");
    }

    @Test
    @DisplayName("OpenAI 요청은 developer 지시·strict JSON 스키마·저장 끔으로 보내고, 출력 텍스트와 추론 토큰을 읽는다")
    void generatesFeedbackWithOpenAi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();
        OpenAiClient openAiClient = new OpenAiClient(builder, new OpenAiProperties("openai-key", "gpt-6.1-terra",
                "low", URI.create("https://api.openai.com"), Duration.ofSeconds(30)), calls::add);
        openAi.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(header("Authorization", "Bearer openai-key"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.reasoning.effort").value("low"))
                .andExpect(jsonPath("$.input[0].role").value("developer"))
                .andExpect(jsonPath("$.input[1].content[0].text").value(org.hamcrest.Matchers.containsString("답변: 검증")))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.name").value("feedback"))
                .andRespond(withSuccess("""
                        {"id":"resp_1","status":"completed","output":[
                          {"type":"reasoning","content":[]},
                          {"type":"message","content":[{"type":"output_text","text":"%s"}]}],
                         "usage":{"input_tokens":700,"output_tokens":260,"output_tokens_details":{"reasoning_tokens":110}}}
                        """.formatted("{\\\"observation\\\":\\\"좋아요.\\\",\\\"suggestion\\\":null,"
                        + "\\\"suggestionFocus\\\":null,\\\"previousReview\\\":null}"), MediaType.APPLICATION_JSON));

        GeneratedFeedback feedback = new LlmFeedbackGenerator(openAiClient)
                .generate(new FeedbackInput("질문", "검증을 맡았습니다.", null));

        assertThat(feedback.suggestion()).isNull();
        assertThat(feedback.generator()).isEqualTo("gpt-6.1-terra/feedback-v1");
        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.outputTokens()).isEqualTo(260);
            assertThat(call.thoughtTokens()).isEqualTo(110);
        });
    }

    @Test
    @DisplayName("OpenAI가 거절하면 REFUSAL로 실패시킨다")
    void openAiRefusalFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer openAi = MockRestServiceServer.bindTo(builder).build();
        OpenAiClient openAiClient = new OpenAiClient(builder, new OpenAiProperties("k", "gpt-6.1-terra", "low",
                URI.create("https://api.openai.com"), Duration.ofSeconds(30)), null);
        openAi.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess("""
                {"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"no"}]}],
                 "usage":{"input_tokens":1,"output_tokens":1}}""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new LlmFollowUpQuestionGenerator(openAiClient)
                .generate(new FollowUpInput("q", "a", List.of())))
                .isInstanceOf(AiGenerationException.class)
                .extracting("errorType").isEqualTo("REFUSAL");
    }

    private static String response(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return """
                {"id":"i-1","status":"completed","steps":[
                  {"type":"model_output","content":[{"type":"text","text":"%s"}]}],
                 "usage":{"total_input_tokens":812,"total_output_tokens":120,"total_thought_tokens":300}}
                """.formatted(escaped);
    }
}
