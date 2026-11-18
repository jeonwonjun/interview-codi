package io.interviewcodi.backend.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.interviewcodi.backend.practice.client.AiGenerationException;
import io.interviewcodi.backend.practice.client.FeedbackGenerator.FeedbackInput;
import io.interviewcodi.backend.practice.client.FeedbackGenerator.GeneratedFeedback;
import io.interviewcodi.backend.practice.client.FollowUpQuestionGenerator.FollowUpInput;
import io.interviewcodi.backend.practice.client.GeminiClient;
import io.interviewcodi.backend.practice.client.GeminiProperties;
import io.interviewcodi.backend.practice.client.LlmCall;
import io.interviewcodi.backend.practice.client.LlmFeedbackGenerator;
import io.interviewcodi.backend.practice.client.LlmFollowUpQuestionGenerator;
import io.interviewcodi.backend.practice.client.OpenAiClient;
import io.interviewcodi.backend.practice.client.OpenAiProperties;
import io.interviewcodi.backend.practice.client.StructuredLlmClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 텍스트 연습의 피드백·꼬리질문 생성을 실제 LLM(OpenAI·Gemini)으로 평가한다. 일반 테스트에서 빠지고 {@code ./gradlew aiEval}로만 돈다.
 * <p>
 * 실제 호출 전에는 계획(요청 수·최대 출력 토큰·유료 기준 최대 비용)만 출력한다. {@code -Deval.execute=true}가 있어야 호출한다.
 * 키는 환경 변수나 저장소 루트 {@code .env}의 {@code OPENAI_API_KEY}·{@code GEMINI_API_KEY}에서 읽고, 화면·보고서에 남기지 않는다.
 * 평가 답변은 직접 만든 예시라 개인정보가 없으므로 무료 등급 키로 돌릴 수 있다.
 * <p>
 * 결과는 {@code build/reports/ai-eval/}에 모델별 보고서로 남긴다. 자동 검사는 형식·금지 표현·개선점 기준만 보고,
 * 내용의 타당성(지어낸 사실이 없는지 등)은 보고서를 사람이 읽어 판정한다.
 */
@Tag("eval")
class TextPracticeAiEval {

    /**
     * 1M 토큰당 [입력, 출력] 달러, 표준 등급. 2026-11-19 각 사 가격표. Gemini 3.8 Flash는 2027-01-01 이후 가격.
     * OpenAI 출력 토큰은 추론 토큰을 포함하고, Gemini는 생각 토큰을 따로 센다.
     */
    private static final Map<String, double[]> PRICES = Map.of(
            "gpt-6.1-terra", new double[] {1.00, 5.00},
            "gpt-6-luna", new double[] {0.10, 0.50},
            "gpt-6.2-sol", new double[] {2.00, 10.00},
            "gemini-3.8-flash", new double[] {1.50, 7.50},
            "gemini-3.6-flash", new double[] {1.50, 7.50},
            "gemini-3.5-flash-lite", new double[] {0.30, 2.50},
            "gemini-3.1-flash-lite", new double[] {0.25, 1.50},
            "gemini-4-hydrogen", new double[] {1.50, 7.50});
    private static final int FEEDBACK_MAX_OUTPUT = 2_048;
    private static final int FOLLOW_UP_MAX_OUTPUT = 1_024;
    private static final Pattern FORBIDDEN = Pattern.compile("점수|등급|합격|불합격|\\d+\\s*점|/\\s*10|만점");

    private static final List<String> MODELS = Arrays.stream(
            System.getProperty("eval.models", "gpt-6.1-terra,gpt-6-luna").split(","))
            .map(String::strip).filter(model -> !model.isEmpty()).toList();
    private static final boolean EXECUTE = Boolean.parseBoolean(System.getProperty("eval.execute", "false"));
    private static final long DELAY_MILLIS = Long.parseLong(System.getProperty("eval.delayMs", "4000"));
    /** 생각·추론 깊이. Gemini thinking_level, OpenAI reasoning.effort에 그대로 쓴다. */
    private static final String THINKING = System.getProperty("eval.thinkingLevel", "low");
    /** 앞에서부터 몇 개만 돌릴지. 첫 호출로 응답 형식을 확인할 때 1로 둔다. */
    private static final int LIMIT = Integer.parseInt(System.getProperty("eval.limit", "0"));

    record EvalCase(String id, String note, String question, String answer, String previousSuggestion,
                    List<String> expectedFocus) {
    }

    @Test
    @DisplayName("피드백·꼬리질문 생성을 실제 모델로 평가한다")
    void evaluate() throws Exception {
        List<EvalCase> cases = loadCases();
        int requests = MODELS.size() * cases.size() * 2;
        double maxCost = MODELS.stream().mapToDouble(model -> cost(model,
                cases.size() * 2_000L, cases.size() * (long) (FEEDBACK_MAX_OUTPUT + FOLLOW_UP_MAX_OUTPUT))).sum();
        System.out.printf("[계획] 모델 %s, 예시 %d개, 요청 %d건(피드백·꼬리질문), 생각 깊이 %s, 요청 간격 %dms%n",
                MODELS, cases.size(), requests, THINKING, DELAY_MILLIS);
        System.out.printf("[계획] 유료 등급 기준 최대 비용(입력 2,000토큰·출력 상한 가정) $%.4f. 무료 등급 키면 $0%n", maxCost);
        if (!EXECUTE) {
            System.out.println("[계획] 실제 호출하려면 -Deval.execute=true 를 붙여 다시 실행한다.");
            return;
        }
        Path dir = Path.of("build/reports/ai-eval");
        Files.createDirectories(dir);
        for (String model : MODELS) {
            String report = evaluateModel(model, cases);
            Path out = dir.resolve(model + "-" + THINKING + ".md");
            Files.writeString(out, report);
            System.out.println(report);
            System.out.println("[보고서] " + out.toAbsolutePath());
        }
    }

    private String evaluateModel(String model, List<EvalCase> cases) throws Exception {
        List<LlmCall> calls = Collections.synchronizedList(new ArrayList<>());
        StructuredLlmClient client = clientFor(model, calls);
        LlmFeedbackGenerator feedbackGenerator = new LlmFeedbackGenerator(client);
        LlmFollowUpQuestionGenerator followUpGenerator = new LlmFollowUpQuestionGenerator(client);

        StringBuilder detail = new StringBuilder();
        int feedbackPass = 0;
        int followUpPass = 0;
        for (EvalCase evalCase : cases) {
            detail.append("\n### ").append(evalCase.id()).append(" — ").append(evalCase.note()).append("\n\n")
                    .append("- 질문: ").append(evalCase.question()).append('\n')
                    .append("- 답변: ").append(evalCase.answer()).append('\n');
            if (evalCase.previousSuggestion() != null) {
                detail.append("- 직전 개선점: ").append(evalCase.previousSuggestion()).append('\n');
            }
            detail.append("- 기대 기준: ").append(evalCase.expectedFocus()).append("\n\n");

            List<String> problems = new ArrayList<>();
            try {
                GeneratedFeedback feedback = withRetry(() -> feedbackGenerator.generate(
                        new FeedbackInput(evalCase.question(), evalCase.answer(), evalCase.previousSuggestion())));
                problems.addAll(checkFeedback(evalCase, feedback));
                detail.append("**피드백** `").append(feedback.suggestionFocus()).append("`\n\n")
                        .append("- 관찰: ").append(feedback.observation()).append('\n')
                        .append("- 바꿔 볼 한 가지: ").append(feedback.suggestion()).append('\n');
                if (feedback.previousReview() != null) {
                    detail.append("- 지난 개선점: ").append(feedback.previousReview()).append('\n');
                }
            } catch (AiGenerationException ex) {
                problems.add("생성 실패 " + ex.getErrorType());
            }
            feedbackPass += problems.isEmpty() ? 1 : 0;
            detail.append("- 자동 검사: ").append(problems.isEmpty() ? "통과" : String.join(", ", problems)).append("\n\n");
            Thread.sleep(DELAY_MILLIS);

            List<String> followUpProblems = new ArrayList<>();
            try {
                String question = withRetry(() -> followUpGenerator.generate(new FollowUpInput(evalCase.question(),
                        evalCase.answer(), List.of(evalCase.question())))).content();
                followUpProblems.addAll(checkFollowUp(evalCase, question));
                detail.append("**꼬리질문**: ").append(question).append('\n');
            } catch (AiGenerationException ex) {
                followUpProblems.add("생성 실패 " + ex.getErrorType());
            }
            followUpPass += followUpProblems.isEmpty() ? 1 : 0;
            detail.append("- 자동 검사: ")
                    .append(followUpProblems.isEmpty() ? "통과" : String.join(", ", followUpProblems)).append('\n');
            Thread.sleep(DELAY_MILLIS);
        }
        return summary(model, cases.size(), feedbackPass, followUpPass, calls) + "\n## 사례별 결과\n" + detail;
    }

    /**
     * 서버 쪽 일시 오류(503 수요 급증, 429 한도)는 품질과 따로 보려고 10초·20초 간격으로 두 번까지 다시 시도한다.
     * 다시 시도한 호출도 보고서의 오류 목록과 성공률에 그대로 남는다(가용성 지표).
     */
    private static <T> T withRetry(java.util.function.Supplier<T> call) throws InterruptedException {
        for (int attempt = 0; ; attempt++) {
            try {
                return call.get();
            } catch (AiGenerationException ex) {
                boolean transientError = ex.getErrorType().equals("HTTP_503") || ex.getErrorType().equals("HTTP_429");
                if (!transientError || attempt >= 2) {
                    throw ex;
                }
                Thread.sleep(10_000L * (attempt + 1));
            }
        }
    }

    /** 모델 이름으로 제공자를 고른다. 서비스와 같은 {@link StructuredLlmClient} 구현을 쓴다. */
    private static StructuredLlmClient clientFor(String model, List<LlmCall> calls) throws IOException {
        Duration timeout = Duration.ofSeconds(30);
        if (model.startsWith("gpt-")) {
            return new OpenAiClient(new OpenAiProperties(loadApiKey("OPENAI_API_KEY"), model, THINKING,
                    URI.create("https://api.openai.com"), timeout), calls::add);
        }
        if (model.startsWith("gemini-")) {
            return new GeminiClient(new GeminiProperties(loadApiKey("GEMINI_API_KEY"), model, THINKING,
                    URI.create("https://generativelanguage.googleapis.com"), timeout), calls::add);
        }
        throw new IllegalArgumentException("알 수 없는 모델 제공자: " + model);
    }

    private static List<String> checkFeedback(EvalCase evalCase, GeneratedFeedback feedback) {
        List<String> problems = new ArrayList<>();
        if (!evalCase.expectedFocus().contains(feedback.suggestionFocus())) {
            problems.add("기준 " + feedback.suggestionFocus() + " (기대 " + evalCase.expectedFocus() + ")");
        }
        String all = feedback.observation() + " " + feedback.suggestion() + " " + feedback.previousReview();
        if (FORBIDDEN.matcher(all).find()) {
            problems.add("평가 표현 사용");
        }
        if ((evalCase.previousSuggestion() != null) != (feedback.previousReview() != null)) {
            problems.add("재답변 확인 " + (feedback.previousReview() == null ? "누락" : "불필요"));
        }
        if (feedback.suggestion() != null && sentences(feedback.suggestion()) > 2) {
            problems.add("개선점이 2문장 초과");
        }
        return problems;
    }

    private static List<String> checkFollowUp(EvalCase evalCase, String question) {
        List<String> problems = new ArrayList<>();
        if (question.chars().filter(ch -> ch == '?').count() > 1) {
            problems.add("질문이 둘 이상");
        }
        if (question.strip().equals(evalCase.question().strip())) {
            problems.add("같은 질문 반복");
        }
        if (question.length() > 120) {
            problems.add("120자 초과");
        }
        return problems;
    }

    private static int sentences(String text) {
        return (int) Arrays.stream(text.split("[.!?]\\s*")).filter(part -> !part.isBlank()).count();
    }

    private static String summary(String model, int cases, int feedbackPass, int followUpPass,
                                  List<LlmCall> calls) {
        StringBuilder table = new StringBuilder();
        for (String purpose : List.of("feedback", "follow-up")) {
            List<LlmCall> ok = calls.stream()
                    .filter(call -> call.purpose().equals(purpose) && call.status().equals("completed")).toList();
            List<Long> latencies = ok.stream().map(LlmCall::latencyMillis).sorted().toList();
            double avgIn = ok.stream().mapToInt(LlmCall::inputTokens).average().orElse(0);
            double avgOut = ok.stream().mapToInt(LlmCall::outputTokens).average().orElse(0);
            double avgThought = ok.stream().mapToInt(LlmCall::thoughtTokens).average().orElse(0);
            long failed = calls.stream().filter(call -> call.purpose().equals(purpose)
                    && !call.status().equals("completed")).count();
            table.append("| %s | %d / %d | %d / %d / %d ms | %.0f / %.0f / %.0f | $%.5f |%n".formatted(purpose,
                    ok.size(), ok.size() + failed, percentile(latencies, 0.5), percentile(latencies, 0.95),
                    latencies.isEmpty() ? 0 : latencies.getLast(), avgIn, avgOut, avgThought,
                    cost(model, Math.round(avgIn), Math.round(model.startsWith("gpt-") ? avgOut : avgOut + avgThought))));
        }
        return """
                # %s 평가 (%s)

                조건: 예시 %d개 × 피드백·꼬리질문, 생각 깊이 %s, 요청 간격 %dms, 순차 호출. 프롬프트 feedback-v1, follow-up-v1.

                | 자동 검사 통과 | 피드백 %d / %d | 꼬리질문 %d / %d |
                | --- | --- | --- |

                | 작업 | 성공 / 전체 | 지연 p50 / p95 / 최대 | 평균 토큰 입력 / 출력 / 생각 | 유료 기준 호출당 비용 |
                | --- | --- | --- | --- | --- |
                %s
                오류: %s

                비용은 표준 등급 단가 기준이다. OpenAI는 출력 토큰(추론 포함), Gemini는 출력+생각 토큰으로 계산한다.
                """.formatted(model, Instant.now(), cases, THINKING, DELAY_MILLIS, feedbackPass, cases, followUpPass,
                cases, table, calls.stream().map(LlmCall::status).filter(status -> !status.equals("completed"))
                        .toList());
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        return sorted.get((int) Math.min(sorted.size() - 1, Math.ceil(p * sorted.size()) - 1));
    }

    private static double cost(String model, long inputTokens, long outputTokens) {
        double[] price = PRICES.getOrDefault(model, new double[] {0, 0});
        return (inputTokens * price[0] + outputTokens * price[1]) / 1_000_000;
    }

    private static List<EvalCase> loadCases() throws IOException {
        try (InputStream in = TextPracticeAiEval.class.getResourceAsStream("/eval/text-practice-v1.json")) {
            EvalCase[] cases = JsonMapper.builder().build().readValue(in, EvalCase[].class);
            assertThat(cases).isNotEmpty();
            List<EvalCase> all = List.of(cases);
            return LIMIT > 0 ? all.subList(0, Math.min(LIMIT, all.size())) : all;
        }
    }

    /** 환경 변수, 없으면 저장소 루트·backend 폴더의 .env에서 키만 읽는다. 값은 출력하지 않는다. */
    private static String loadApiKey(String name) throws IOException {
        return findApiKey(name).orElseThrow(() -> new IllegalStateException(name + "가 없습니다."));
    }

    private static Optional<String> findApiKey(String name) throws IOException {
        String fromEnv = System.getenv(name);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Optional.of(fromEnv.strip());
        }
        for (Path path : List.of(Path.of("../.env"), Path.of(".env"))) {
            if (!Files.exists(path)) {
                continue;
            }
            for (String line : Files.readAllLines(path)) {
                if (line.startsWith(name + "=") && line.length() > name.length() + 1) {
                    return Optional.of(line.substring(name.length() + 1).strip());
                }
            }
        }
        return Optional.empty();
    }
}
