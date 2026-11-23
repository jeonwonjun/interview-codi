package io.interviewcodi.backend.load;

import static org.assertj.core.api.Assertions.assertThat;

import io.interviewcodi.backend.TestcontainersConfiguration;
import io.interviewcodi.backend.auth.TestLogin;
import io.interviewcodi.backend.user.service.UserAccountService;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 피드백 파이프라인(답변 제출 → 큐 → 워커 → AI → 반영)의 처리량과 지연을 잰다. 설계 문서 5-1의 기준선 측정용.
 * <p>
 * 실제 서버(무작위 포트)·PostgreSQL(Testcontainers)·워커를 띄우고, AI는 가짜 생성기의 지연으로 흉내 낸다.
 * 가상 사용자는 1분에 한 번 답하고, 결과가 나올 때까지 1.5초마다 폴링한다. 시작 시각은 첫 1분에 고르게 흩는다.
 * <p>
 * 지연은 DB의 시각으로 계산한다: 큐 대기 = first_claimed_at - created_at, 전체 = finished_at - created_at.
 * 폴링 응답 시간은 같은 JVM의 클라이언트로 쟀으므로 참고값이다(동기 API는 k6로 따로 잰다).
 * <p>
 * 실행: {@code ./gradlew loadTest -Dload.users=30 -Dload.workerConcurrency=4 -Dload.durationSeconds=180
 * -Dload.followUpRate=0.3}
 */
@Tag("load")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FeedbackPipelineLoadTest {

    private static final int USERS = intProperty("load.users", 30);
    private static final int WORKER_CONCURRENCY = intProperty("load.workerConcurrency", 10);
    private static final Duration DURATION = Duration.ofSeconds(intProperty("load.durationSeconds", 180));
    private static final String DELAY_MIN = System.getProperty("load.aiDelayMin", "3s");
    private static final String DELAY_MAX = System.getProperty("load.aiDelayMax", "8s");
    /** 음성으로 답할 확률. 음성 답변은 인식(TRANSCRIBE) → 버전 1 + 피드백 순서로 처리된다. */
    private static final double VOICE_RATE = Double.parseDouble(System.getProperty("load.voiceRate", "0"));
    private static final String STT_DELAY_MIN = System.getProperty("load.sttDelayMin", "2s");
    private static final String STT_DELAY_MAX = System.getProperty("load.sttDelayMax", "6s");
    /** 인식만 꺼내는 워커 슬롯 수(설계 5-2의 B안). 0이면 모든 슬롯이 모든 종류를 꺼낸다. */
    private static final int TRANSCRIBE_SLOTS = intProperty("load.transcribeSlots", 0);
    // webm(EBML) 서명으로 시작하는 가짜 녹음. 가짜 인식기는 내용을 보지 않는다.
    private static final byte[] FAKE_WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0x02, 0x03, 0x04};
    /** 피드백을 받은 뒤 꼬리질문을 요청할 확률. 받으면 다음 답변은 그 꼬리질문에 한다. */
    private static final double FOLLOW_UP_RATE = Double.parseDouble(System.getProperty("load.followUpRate", "0"));
    private static final Duration SUBMIT_INTERVAL = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(1_500);
    private static final Duration DRAIN_TIMEOUT = Duration.ofMinutes(5);
    private static final Pattern NUMBER = Pattern.compile("\"%s\":(\\d+)");

    @LocalServerPort
    private int port;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private SessionRepository<?> sessionRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ConcurrentLinkedQueue<Long> pollMillis = new ConcurrentLinkedQueue<>();
    private final AtomicInteger httpErrors = new AtomicInteger();
    private final AtomicInteger submitted = new AtomicInteger();
    private final AtomicInteger followUps = new AtomicInteger();
    private final AtomicInteger voiceAnswers = new AtomicInteger();

    @DynamicPropertySource
    static void loadProperties(DynamicPropertyRegistry registry) {
        // 부하 측정은 비용이 들지 않도록 항상 가짜 생성기로 돌린다(.env의 AI_PROVIDER를 덮어쓴다).
        registry.add("practice.ai.provider", () -> "fake");
        registry.add("practice.stt.provider", () -> "fake");
        registry.add("practice.stt.fake.delay-min", () -> STT_DELAY_MIN);
        registry.add("practice.stt.fake.delay-max", () -> STT_DELAY_MAX);
        registry.add("practice.ai.worker.transcribe-slots", () -> TRANSCRIBE_SLOTS);
        registry.add("practice.ai.worker.enabled", () -> "true");
        registry.add("practice.ai.worker.concurrency", () -> WORKER_CONCURRENCY);
        registry.add("practice.ai.fake.delay-min", () -> DELAY_MIN);
        registry.add("practice.ai.fake.delay-max", () -> DELAY_MAX);
    }

    @Test
    @DisplayName("피드백 파이프라인의 처리량과 큐 대기·전체 지연을 잰다")
    void measureFeedbackPipeline() throws Exception {
        Instant start = Instant.now();
        Instant stopSubmitting = start.plus(DURATION);
        AtomicBoolean sampling = new AtomicBoolean(true);
        List<String> backlogSamples = Collections.synchronizedList(new ArrayList<>());

        try (ExecutorService users = Executors.newVirtualThreadPerTaskExecutor();
             ExecutorService sampler = Executors.newVirtualThreadPerTaskExecutor()) {
            sampler.submit(() -> sampleBacklog(start, sampling, backlogSamples));
            for (int i = 0; i < USERS; i++) {
                long userId = userAccountService.findOrCreateByKakaoUserId(9_000_000L + i);
                Cookie cookie = TestLogin.sessionCookie(sessionRepository, userId);
                users.submit(() -> runUser(cookie, start, stopSubmitting));
            }
            users.shutdown();
            users.awaitTermination(DURATION.plusMinutes(2).toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            waitUntilDrained();
            sampling.set(false);
        }

        String report = report(start, backlogSamples);
        System.out.println(report);
        Path out = Path.of("build/reports/load/feedback-pipeline-c" + WORKER_CONCURRENCY + "-t" + TRANSCRIBE_SLOTS
                + "-f" + Math.round(FOLLOW_UP_RATE * 100) + "-v" + Math.round(VOICE_RATE * 100) + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);

        assertThat(httpErrors.get()).as("HTTP 오류").isZero();
        assertThat(count("status in ('PENDING', 'RUNNING')")).as("남은 요청").isZero();
    }

    private void runUser(Cookie cookie, Instant start, Instant stopSubmitting) {
        try {
            long practiceId = extract(send("POST", "/api/practices", "{\"startMode\":\"EXPERIENCE\"}", cookie),
                    "id");
            String detail = send("GET", "/api/practices/" + practiceId, null, cookie);
            long questionId = extract(detail.substring(detail.indexOf("\"questions\"")), "id");
            long spread = Math.min(SUBMIT_INTERVAL.toMillis(), DURATION.toMillis());
            Instant next = start.plusMillis(ThreadLocalRandom.current().nextLong(spread));
            while (next.isBefore(stopSubmitting)) {
                sleepUntil(next);
                String attempt = ThreadLocalRandom.current().nextDouble() < VOICE_RATE
                        ? answerByVoice(practiceId, questionId, cookie)
                        : answerByText(questionId, cookie);
                submitted.incrementAndGet();
                long requestId = extract(attempt.substring(attempt.indexOf("\"feedbackRequest\"")), "id");
                pollUntilFinished(requestId, cookie);
                if (ThreadLocalRandom.current().nextDouble() < FOLLOW_UP_RATE) {
                    long revisionId = extract(attempt.substring(attempt.indexOf("\"currentRevision\"")), "id");
                    String followUp = send("POST", "/api/practices/" + practiceId + "/follow-up-requests",
                            "{\"answerRevisionId\":" + revisionId + "}", cookie);
                    followUps.incrementAndGet();
                    String finished = pollUntilFinished(extract(followUp, "id"), cookie);
                    if (finished.contains("\"SUCCEEDED\"")) {
                        questionId = extract(finished, "questionId");
                    }
                }
                next = next.plus(SUBMIT_INTERVAL);
            }
        } catch (Exception ex) {
            httpErrors.incrementAndGet();
            ex.printStackTrace();
        }
    }

    /** 텍스트 답변. 응답(시도)에 버전과 피드백 요청이 바로 있다. */
    private String answerByText(long questionId, Cookie cookie) throws Exception {
        String body = "{\"clientRequestId\":\"" + UUID.randomUUID()
                + "\",\"content\":\"예약 기능에서 날짜 검증과 오류 응답을 맡았습니다. 입력값이 비어 있으면 400을 돌려주도록 했습니다.\"}";
        return send("POST", "/api/questions/" + questionId + "/answers", body, cookie);
    }

    /**
     * 음성 답변. 화면처럼 인식 요청을 폴링하다가 끝나면 상세를 다시 읽어, 생긴 버전과 피드백 요청이 든 시도를 돌려준다.
     */
    private String answerByVoice(long practiceId, long questionId, Cookie cookie) throws Exception {
        String boundary = "load-" + UUID.randomUUID();
        byte[] body = multipart(boundary, UUID.randomUUID());
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/questions/" + questionId + "/voice-answers"))
                .header("Cookie", cookie.getName() + "=" + cookie.getValue())
                .header("Origin", "http://localhost:" + port)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            httpErrors.incrementAndGet();
            throw new IllegalStateException("POST voice-answers -> " + response.statusCode());
        }
        voiceAnswers.incrementAndGet();
        String attempt = response.body();
        long answerId = extract(attempt, "id");
        pollUntilFinished(extract(attempt.substring(attempt.indexOf("\"transcriptionRequest\"")), "id"), cookie);
        String detail = send("GET", "/api/practices/" + practiceId, null, cookie);
        return detail.substring(detail.indexOf("{\"id\":" + answerId + ",\"questionId\""));
    }

    private static byte[] multipart(String boundary, UUID clientRequestId) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"clientRequestId\"\r\n\r\n" + clientRequestId + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"durationMs\"\r\n\r\n60000\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"audio\"; filename=\"answer.webm\"\r\n"
                + "Content-Type: audio/webm\r\n\r\n";
        out.write(head.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(FAKE_WEBM);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private String pollUntilFinished(long requestId, Cookie cookie) throws Exception {
        Instant giveUp = Instant.now().plus(DRAIN_TIMEOUT);
        String status = "";
        while (Instant.now().isBefore(giveUp)) {
            long started = System.nanoTime();
            status = send("GET", "/api/ai-requests/" + requestId, null, cookie);
            pollMillis.add((System.nanoTime() - started) / 1_000_000);
            if (!status.contains("\"PENDING\"") && !status.contains("\"RUNNING\"")) {
                return status;
            }
            Thread.sleep(POLL_INTERVAL);
        }
        return status;
    }

    private String send(String method, String path, String body, Cookie cookie) throws IOException,
            InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", cookie.getName() + "=" + cookie.getValue())
                .header("Origin", "http://localhost:" + port)
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            httpErrors.incrementAndGet();
            throw new IllegalStateException(method + " " + path + " -> " + response.statusCode());
        }
        return response.body();
    }

    private void sampleBacklog(Instant start, AtomicBoolean sampling, List<String> samples) {
        while (sampling.get()) {
            long seconds = Duration.between(start, Instant.now()).toSeconds();
            samples.add(seconds + "s: PENDING " + count("status = 'PENDING'") + ", RUNNING "
                    + count("status = 'RUNNING'"));
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException ex) {
                return;
            }
        }
    }

    private void waitUntilDrained() throws InterruptedException {
        Instant giveUp = Instant.now().plus(DRAIN_TIMEOUT);
        while (count("status in ('PENDING', 'RUNNING')") > 0 && Instant.now().isBefore(giveUp)) {
            Thread.sleep(1_000);
        }
    }

    private int count(String condition) {
        return jdbcClient.sql("select count(*) from ai_requests where " + condition).query(Integer.class).single();
    }

    private String report(Instant start, List<String> backlogSamples) {
        List<Map<String, Object>> rows = jdbcClient.sql("""
                        select kind,
                               count(*) as total,
                               count(*) filter (where status = 'SUCCEEDED') as succeeded,
                               count(*) filter (where status = 'FAILED') as failed,
                               count(*) filter (where status = 'DISCARDED') as discarded,
                               percentile_cont(0.50) within group (order by extract(epoch from first_claimed_at - created_at)) as queue_p50,
                               percentile_cont(0.95) within group (order by extract(epoch from first_claimed_at - created_at)) as queue_p95,
                               percentile_cont(0.99) within group (order by extract(epoch from first_claimed_at - created_at)) as queue_p99,
                               percentile_cont(0.50) within group (order by extract(epoch from finished_at - created_at)) as total_p50,
                               percentile_cont(0.95) within group (order by extract(epoch from finished_at - created_at)) as total_p95,
                               percentile_cont(0.99) within group (order by extract(epoch from finished_at - created_at)) as total_p99
                        from ai_requests
                        group by kind
                        order by kind
                        """)
                .query()
                .listOfRows();
        Map<String, Object> overall = jdbcClient.sql("""
                        select count(*) filter (where status = 'SUCCEEDED') as succeeded,
                               extract(epoch from max(finished_at) - min(created_at)) as span_seconds
                        from ai_requests
                        """)
                .query()
                .singleRow();
        Map<String, Object> voice = jdbcClient.sql("""
                        select count(*) as total,
                               percentile_cont(0.50) within group (order by extract(epoch from f.finished_at - t.created_at)) as p50,
                               percentile_cont(0.95) within group (order by extract(epoch from f.finished_at - t.created_at)) as p95,
                               percentile_cont(0.99) within group (order by extract(epoch from f.finished_at - t.created_at)) as p99
                        from ai_requests t
                        join recordings rec on rec.id = t.recording_id
                        join answer_revisions r on r.answer_id = rec.answer_id and r.revision_no = 1
                        join ai_requests f on f.answer_revision_id = r.id and f.kind = 'FEEDBACK' and f.status = 'SUCCEEDED'
                        where t.kind = 'TRANSCRIBE' and t.status = 'SUCCEEDED'
                        """)
                .query()
                .singleRow();
        List<Long> polls = new ArrayList<>(pollMillis);
        Collections.sort(polls);
        StringBuilder table = new StringBuilder();
        for (Map<String, Object> row : rows) {
            table.append("| %s | %s / %s / %s / %s | %.1f / %.1f / %.1f초 | %.1f / %.1f / %.1f초 |%n".formatted(
                    row.get("kind"), row.get("total"), row.get("succeeded"), row.get("failed"), row.get("discarded"),
                    num(row, "queue_p50"), num(row, "queue_p95"), num(row, "queue_p99"),
                    num(row, "total_p50"), num(row, "total_p95"), num(row, "total_p99")));
        }
        double span = num(overall, "span_seconds");
        long succeeded = ((Number) overall.get("succeeded")).longValue();
        double arrival = (submitted.get() + followUps.get()) / span;
        return """
                # 피드백 파이프라인 측정 (%s)

                조건: 가상 사용자 %d명, 1인당 %d초에 답변 1건, 꼬리질문 요청 확률 %.0f%%, 측정 %d초 후 큐가 빌 때까지 대기. 워커 공용 슬롯 %d + 인식 전용 슬롯 %d, 가짜 AI 지연 %s~%s(균등).
                음성 답변 확률 %.0f%%, 가짜 인식 지연 %s~%s(균등).
                환경: 로컬 1대, 서버·워커·부하 클라이언트가 같은 JVM, PostgreSQL 18.6(Testcontainers).

                제출 %d건(음성 %d건), 꼬리질문 요청 %d건. AI 요청 처리량(성공 ÷ 첫 접수~마지막 완료) %.2f건/초, 평균 도착률 %.2f건/초.

                | 종류 | 전체 / 성공 / 실패 / 버림 | 큐 대기 p50 / p95 / p99 | 전체 지연 p50 / p95 / p99 |
                | --- | --- | --- | --- |
                %s
                음성 답변 제출 → 피드백(인식 접수 ~ 버전 1 피드백 완료) p50 / p95 / p99: %.1f / %.1f / %.1f초 (%s건)

                폴링 응답 p50 / p95 / p99 (참고): %d / %d / %d ms (%d건)

                대기열 표본(15초마다):
                %s
                """.formatted(start, USERS, SUBMIT_INTERVAL.toSeconds(), FOLLOW_UP_RATE * 100, DURATION.toSeconds(),
                WORKER_CONCURRENCY, TRANSCRIBE_SLOTS, DELAY_MIN, DELAY_MAX, VOICE_RATE * 100, STT_DELAY_MIN,
                STT_DELAY_MAX, submitted.get(), voiceAnswers.get(), followUps.get(), succeeded / span,
                arrival + voiceAnswers.get() / span, table, num(voice, "p50"), num(voice, "p95"), num(voice, "p99"),
                voice.get("total"), percentile(polls, 0.50), percentile(polls, 0.95), percentile(polls, 0.99),
                polls.size(), String.join("\n", backlogSamples.stream().map(sample -> "- " + sample).toList()));
    }

    private static double num(Map<String, Object> stats, String key) {
        Object value = stats.get(key);
        return value == null ? Double.NaN : ((Number) value).doubleValue();
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        return sorted.get((int) Math.min(sorted.size() - 1, Math.ceil(p * sorted.size()) - 1));
    }

    private static long extract(String json, String field) {
        Matcher matcher = Pattern.compile(NUMBER.pattern().formatted(field)).matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No " + field + " in " + json);
        }
        return Long.parseLong(matcher.group(1));
    }

    private static void sleepUntil(Instant when) throws InterruptedException {
        long millis = Duration.between(Instant.now(), when).toMillis();
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }

    private static int intProperty(String name, int defaultValue) {
        return Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
    }
}
