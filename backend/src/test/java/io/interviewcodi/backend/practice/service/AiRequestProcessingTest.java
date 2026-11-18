package io.interviewcodi.backend.practice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.interviewcodi.backend.TestcontainersConfiguration;
import io.interviewcodi.backend.auth.TestLogin;
import io.interviewcodi.backend.practice.client.AiGenerationException;
import io.interviewcodi.backend.practice.client.FeedbackGenerator;
import io.interviewcodi.backend.practice.client.FeedbackGenerator.FeedbackInput;
import io.interviewcodi.backend.practice.client.FeedbackGenerator.GeneratedFeedback;
import io.interviewcodi.backend.practice.service.AiRequestQueueService.Claim;
import io.interviewcodi.backend.practice.service.AiRequestQueueService.Outcome;
import io.interviewcodi.backend.user.service.UserAccountService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * AI 요청 큐의 처리·실패·늦은 결과를 확인한다. 워커 스케줄은 꺼 두고({@code config/application.properties})
 * {@link AiRequestProcessingService}를 직접 불러, 생성 도중에 교정·삭제·마치기가 끼는 순서를 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AiRequestProcessingTest {

    private static final String ORIGIN = "http://localhost";
    private static final AtomicLong NEXT_KAKAO_USER_ID = new AtomicLong(3_000_000L);
    private static final GeneratedFeedback FEEDBACK =
            new GeneratedFeedback("핵심 답이 앞에 있어요.", "직접 한 일을 하나 덧붙여 보세요.", "EVIDENCE", null, "test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiRequestProcessingService processing;

    @Autowired
    private AiRequestQueueService queue;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private SessionRepository<?> sessionRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private FeedbackGenerator generator;

    private Cookie me;

    @BeforeEach
    void setUp() {
        // 다른 테스트 클래스가 남긴 대기 요청을 이 테스트의 워커가 처리하지 않도록 정리한다.
        jdbcClient.sql("""
                        update ai_requests
                        set status = 'DISCARDED', discard_reason = 'PRACTICE_DELETED', finished_at = now(),
                            lease_token = null, lease_until = null
                        where status in ('PENDING', 'RUNNING')
                        """)
                .update();
        me = login();
    }

    @Test
    @DisplayName("대기 중인 피드백 요청을 처리하면 피드백이 저장되고 상세와 요청 상태에 나온다")
    void processesFeedback() throws Exception {
        when(generator.generate(any())).thenReturn(FEEDBACK);
        long practiceId = createPractice(me);
        Submitted submitted = submitAnswer(practiceId, "예약 기능을 맡았습니다.", me);

        assertThat(processing.processNext()).isTrue();
        assertThat(processing.processNext()).as("남은 요청 없음").isFalse();

        mockMvc.perform(get("/api/ai-requests/{id}", submitted.requestId()).cookie(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.kind").value("FEEDBACK"))
                .andExpect(jsonPath("$.answerRevisionId").value(submitted.revisionId()))
                .andExpect(jsonPath("$.feedbackId").isNumber());
        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions[0].attempts[0].feedbackRequest.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.questions[0].attempts[0].feedback.suggestion").value(FEEDBACK.suggestion()))
                .andExpect(jsonPath("$.questions[0].attempts[0].feedback.suggestionFocus").value("EVIDENCE"));
        ArgumentCaptor<FeedbackInput> input = ArgumentCaptor.forClass(FeedbackInput.class);
        verify(generator).generate(input.capture());
        assertThat(input.getValue().answer()).isEqualTo("예약 기능을 맡았습니다.");
        assertThat(input.getValue().previousSuggestion()).isNull();
    }

    @Test
    @DisplayName("일시적 실패는 간격을 두고 다시 시도하고, 세 번째 실패면 FAILED로 끝난다")
    void retriesThenFails() throws Exception {
        when(generator.generate(any())).thenThrow(new AiGenerationException("TIMEOUT", null));
        long practiceId = createPractice(me);
        Submitted submitted = submitAnswer(practiceId, "답변", me);

        assertThat(processing.processNext()).isTrue();
        assertThat(requestColumn(submitted.requestId(), "status")).isEqualTo("PENDING");
        assertThat(processing.processNext()).as("다시 시도할 시각 전에는 꺼내지 않는다").isFalse();

        makeAvailableNow(submitted.requestId());
        processing.processNext();
        makeAvailableNow(submitted.requestId());
        processing.processNext();

        assertThat(requestColumn(submitted.requestId(), "status")).isEqualTo("FAILED");
        assertThat(requestColumn(submitted.requestId(), "last_error")).isEqualTo("TIMEOUT");
        assertThat(requestColumn(submitted.requestId(), "attempt_count")).isEqualTo("3");
        verify(generator, times(3)).generate(any());
    }

    @Test
    @DisplayName("피드백 재요청은 실패했을 때만 새로 만들고, 처리 중·성공이면 기존 요청을 돌려준다")
    void retryRequestOnlyAfterFailure() throws Exception {
        long practiceId = createPractice(me);
        Submitted submitted = submitAnswer(practiceId, "답변", me);

        mockMvc.perform(write(post("/api/answer-revisions/{id}/feedback-requests", submitted.revisionId()), me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submitted.requestId()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        jdbcClient.sql("update ai_requests set status = 'FAILED', finished_at = now() where id = :id")
                .param("id", submitted.requestId()).update();
        String retried = mockMvc.perform(
                        write(post("/api/answer-revisions/{id}/feedback-requests", submitted.revisionId()), me))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long retriedId = ((Number) JsonPath.read(retried, "$.id")).longValue();
        assertThat(retriedId).isNotEqualTo(submitted.requestId());

        when(generator.generate(any())).thenReturn(FEEDBACK);
        processing.processNext();
        mockMvc.perform(write(post("/api/answer-revisions/{id}/feedback-requests", submitted.revisionId()), me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(retriedId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        verify(generator, times(1)).generate(any());
    }

    @Test
    @DisplayName("교정된 버전이나 마친 연습에는 피드백을 다시 요청할 수 없다")
    void retryRequestRejectedForStaleRevisionOrCompletedPractice() throws Exception {
        long practiceId = createPractice(me);
        Submitted submitted = submitAnswer(practiceId, "답변", me);
        revise(submitted.answerId(), 1, "교정한 답변");

        mockMvc.perform(write(post("/api/answer-revisions/{id}/feedback-requests", submitted.revisionId()), me))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_LATEST_REVISION"));

        mockMvc.perform(write(post("/api/practices/{id}/complete", practiceId), me)).andExpect(status().isOk());
        long revision2 = jdbcClient.sql("select id from answer_revisions where answer_id = :a and revision_no = 2")
                .param("a", submitted.answerId()).query(Long.class).single();
        mockMvc.perform(write(post("/api/answer-revisions/{id}/feedback-requests", revision2), me))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRACTICE_COMPLETED"));
    }

    @Test
    @DisplayName("피드백 생성 중에 연습을 마쳐도 결과는 저장되고 완료 상태와 시각은 그대로다")
    void feedbackAfterCompletionIsSaved() throws Exception {
        long practiceId = createPractice(me);
        Submitted submitted = submitAnswer(practiceId, "답변", me);
        when(generator.generate(any())).thenAnswer(invocation -> {
            complete(practiceId);
            return FEEDBACK;
        });

        processing.processNext();

        assertThat(requestColumn(submitted.requestId(), "status")).isEqualTo("SUCCEEDED");
        String completedAt = practiceColumn(practiceId, "completed_at");
        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.questions[0].attempts[0].feedback.observation").value(FEEDBACK.observation()));
        assertThat(practiceColumn(practiceId, "completed_at")).isEqualTo(completedAt);
    }

    @Test
    @DisplayName("피드백 생성 중에 연습을 삭제하면 결과를 버린다")
    void discardsWhenPracticeDeletedDuringGeneration() throws Exception {
        long practiceId = createPractice(me);
        Submitted submitted = submitAnswer(practiceId, "답변", me);
        when(generator.generate(any())).thenAnswer(invocation -> {
            mockMvc.perform(write(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .delete("/api/practices/{id}", practiceId), me)).andExpect(status().isNoContent());
            return FEEDBACK;
        });

        processing.processNext();

        assertThat(requestColumn(submitted.requestId(), "status")).isEqualTo("DISCARDED");
        assertThat(requestColumn(submitted.requestId(), "discard_reason")).isEqualTo("PRACTICE_DELETED");
        assertThat(countFeedback(submitted.revisionId())).isZero();
    }

    @Test
    @DisplayName("피드백 생성 중에 답변이 교정되면 교정 전 버전의 결과는 버린다")
    void discardsStaleRevisionResult() throws Exception {
        long practiceId = createPractice(me);
        Submitted submitted = submitAnswer(practiceId, "처음 답변", me);
        when(generator.generate(any())).thenAnswer(invocation -> {
            revise(submitted.answerId(), 1, "교정한 답변");
            return FEEDBACK;
        });

        processing.processNext();

        assertThat(requestColumn(submitted.requestId(), "status")).isEqualTo("DISCARDED");
        assertThat(requestColumn(submitted.requestId(), "discard_reason")).isEqualTo("SUPERSEDED");
        assertThat(countFeedback(submitted.revisionId())).isZero();
        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions[0].attempts[0].currentRevision.revisionNo").value(2))
                .andExpect(jsonPath("$.questions[0].attempts[0].feedback").isEmpty())
                .andExpect(jsonPath("$.questions[0].attempts[0].feedbackRequest.status").value("PENDING"));
    }

    @Test
    @DisplayName("교정 후 새 버전의 결과가 먼저 도착하고 이전 버전의 결과가 늦게 도착해도, 화면에는 새 버전의 피드백만 남는다")
    void lateResultOfOldRevisionDoesNotOverwriteNewOne() throws Exception {
        long practiceId = createPractice(me);
        Submitted submitted = submitAnswer(practiceId, "처음 답변", me);
        GeneratedFeedback forRevision2 = new GeneratedFeedback("교정한 답변을 봤어요.", null, null, null, "test");
        AtomicLong calls = new AtomicLong();
        when(generator.generate(any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                // 버전 1을 생성하는 동안 교정이 저장되고, 버전 2의 요청이 먼저 처리돼 결과가 도착한다.
                revise(submitted.answerId(), 1, "교정한 답변");
                assertThat(processing.processNext()).isTrue();
                return FEEDBACK;
            }
            return forRevision2;
        });

        processing.processNext();

        assertThat(requestColumn(submitted.requestId(), "discard_reason")).isEqualTo("SUPERSEDED");
        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions[0].attempts[0].currentRevision.revisionNo").value(2))
                .andExpect(jsonPath("$.questions[0].attempts[0].feedback.observation").value(forRevision2.observation()))
                .andExpect(jsonPath("$.questions[0].attempts[0].feedback.suggestion").isEmpty())
                .andExpect(jsonPath("$.questions[0].attempts[0].feedbackRequest.status").value("SUCCEEDED"));
        assertThat(countFeedback(submitted.revisionId())).isZero();
    }

    @Test
    @DisplayName("처리하던 워커가 멈춰 임대가 끝나면 다른 워커가 다시 처리하고, 늦게 돌아온 원래 결과는 반영하지 않는다")
    void reclaimsExpiredLeaseAndIgnoresStaleWorker() throws Exception {
        when(generator.generate(any())).thenReturn(FEEDBACK);
        long practiceId = createPractice(me);
        Submitted submitted = submitAnswer(practiceId, "답변", me);
        Claim stuck = queue.claim().orElseThrow();
        jdbcClient.sql("update ai_requests set lease_until = now() - interval '1 second' where id = :id")
                .param("id", submitted.requestId()).update();

        processing.processNext();
        Outcome lateResult = queue.applyFeedback(stuck.request(), FEEDBACK);

        assertThat(lateResult).isEqualTo(Outcome.LEASE_LOST);
        assertThat(requestColumn(submitted.requestId(), "status")).isEqualTo("SUCCEEDED");
        assertThat(requestColumn(submitted.requestId(), "attempt_count")).isEqualTo("2");
        assertThat(countFeedback(submitted.revisionId())).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 워커가 동시에 큐를 비워도 요청마다 한 번씩만 처리한다")
    void concurrentWorkersProcessEachRequestOnce() throws Exception {
        when(generator.generate(any())).thenReturn(FEEDBACK);
        long practiceId = createPractice(me);
        int answers = 12;
        for (int i = 0; i < answers; i++) {
            submitAnswer(practiceId, "답변 " + i, me);
        }
        int workers = 4;
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> drain = () -> {
            start.await();
            int processed = 0;
            while (processing.processNext()) {
                processed++;
            }
            return processed;
        };

        List<Integer> processed;
        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            List<Future<Integer>> futures = IntStream.range(0, workers).mapToObj(i -> executor.submit(drain)).toList();
            start.countDown();
            processed = futures.stream().map(AiRequestProcessingTest::await).toList();
        }

        assertThat(processed.stream().mapToInt(Integer::intValue).sum()).isEqualTo(answers);
        verify(generator, times(answers)).generate(any());
        assertThat(jdbcClient.sql("""
                        select count(*) from ai_requests where practice_id = :id and status = 'SUCCEEDED'
                        """)
                .param("id", practiceId).query(Integer.class).single()).isEqualTo(answers);
    }

    @Test
    @DisplayName("재답변의 피드백에는 직전 시도의 개선점을 함께 넘긴다")
    void reanswerIncludesPreviousSuggestion() throws Exception {
        when(generator.generate(any())).thenReturn(FEEDBACK);
        long practiceId = createPractice(me);
        submitAnswer(practiceId, "첫 답변", me);
        processing.processNext();
        submitAnswer(practiceId, "다시 답변", me);

        processing.processNext();

        ArgumentCaptor<FeedbackInput> input = ArgumentCaptor.forClass(FeedbackInput.class);
        verify(generator, times(2)).generate(input.capture());
        assertThat(input.getAllValues().get(1).answer()).isEqualTo("다시 답변");
        assertThat(input.getAllValues().get(1).previousSuggestion()).isEqualTo(FEEDBACK.suggestion());
    }

    @Test
    @DisplayName("다른 사용자의 AI 요청과 답변 버전은 404로 응답한다")
    void hidesOtherUsersRequests() throws Exception {
        long practiceId = createPractice(me);
        Submitted submitted = submitAnswer(practiceId, "답변", me);
        Cookie other = login();

        mockMvc.perform(get("/api/ai-requests/{id}", submitted.requestId()).cookie(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_REQUEST_NOT_FOUND"));
        mockMvc.perform(write(post("/api/answer-revisions/{id}/feedback-requests", submitted.revisionId()), other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANSWER_REVISION_NOT_FOUND"));
    }

    private record Submitted(long answerId, long revisionId, long requestId) {
    }

    private Cookie login() {
        return TestLogin.sessionCookie(sessionRepository,
                userAccountService.findOrCreateByKakaoUserId(NEXT_KAKAO_USER_ID.incrementAndGet()));
    }

    private long createPractice(Cookie login) throws Exception {
        String body = mockMvc.perform(write(post("/api/practices"), login).content("{\"startMode\":\"EXPERIENCE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Submitted submitAnswer(long practiceId, String content, Cookie login) throws Exception {
        long questionId = jdbcClient.sql("select id from questions where practice_id = :id order by order_no desc limit 1")
                .param("id", practiceId).query(Long.class).single();
        String body = mockMvc.perform(write(post("/api/questions/{id}/answers", questionId), login)
                        .content("{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Submitted(((Number) JsonPath.read(body, "$.id")).longValue(),
                ((Number) JsonPath.read(body, "$.currentRevision.id")).longValue(),
                ((Number) JsonPath.read(body, "$.feedbackRequest.id")).longValue());
    }

    private void complete(long practiceId) throws Exception {
        mockMvc.perform(write(post("/api/practices/{id}/complete", practiceId), me)).andExpect(status().isOk());
    }

    private void revise(long answerId, int baseRevisionNo, String content) throws Exception {
        mockMvc.perform(write(post("/api/answers/{id}/revisions", answerId), me)
                        .content("{\"baseRevisionNo\":" + baseRevisionNo + ",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated());
    }

    private void makeAvailableNow(long requestId) {
        jdbcClient.sql("update ai_requests set available_at = now() where id = :id").param("id", requestId).update();
    }

    private String requestColumn(long requestId, String column) {
        return jdbcClient.sql("select " + column + "::text from ai_requests where id = :id")
                .param("id", requestId).query(String.class).single();
    }

    private String practiceColumn(long practiceId, String column) {
        return jdbcClient.sql("select " + column + "::text from practices where id = :id")
                .param("id", practiceId).query(String.class).single();
    }

    private int countFeedback(long revisionId) {
        return jdbcClient.sql("select count(*) from feedback where answer_revision_id = :id")
                .param("id", revisionId).query(Integer.class).single();
    }

    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder, Cookie login) {
        return builder.cookie(login).header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON);
    }

    private static Integer await(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
