package io.interviewcodi.backend.practice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.interviewcodi.backend.TestcontainersConfiguration;
import io.interviewcodi.backend.auth.TestLogin;
import io.interviewcodi.backend.practice.domain.ExperienceQuestion;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 연습 생성·조회·답변 제출·마치기·삭제를 HTTP API로 확인한다. AI 요청은 이 단계에서 PENDING으로 쌓이기만 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PracticeApiTest {

    /** MockMvc 요청의 주소(http://localhost). 상태를 바꾸는 요청은 Origin 검사를 통과해야 한다. */
    private static final String ORIGIN = "http://localhost";

    private static final AtomicLong NEXT_KAKAO_USER_ID = new AtomicLong(2_000_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private SessionRepository<?> sessionRepository;

    private Cookie me;
    private Cookie other;

    @BeforeEach
    void logIn() {
        me = TestLogin.sessionCookie(sessionRepository,
                userAccountService.findOrCreateByKakaoUserId(NEXT_KAKAO_USER_ID.incrementAndGet()));
        other = TestLogin.sessionCookie(sessionRepository,
                userAccountService.findOrCreateByKakaoUserId(NEXT_KAKAO_USER_ID.incrementAndGet()));
    }

    @Test
    @DisplayName("자료 없이 연습을 시작하면 첫 고정 질문이 현재 질문으로 저장된다")
    void createsPracticeWithFirstPresetQuestion() throws Exception {
        mockMvc.perform(write(post("/api/practices"), me).content("{\"startMode\":\"EXPERIENCE\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/practices/")))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.canProceed").value(true))
                .andExpect(jsonPath("$.readOnlyReason").isEmpty())
                .andExpect(jsonPath("$.questions.length()").value(1))
                .andExpect(jsonPath("$.questions[0].orderNo").value(1))
                .andExpect(jsonPath("$.questions[0].sourceKind").value("EXPERIENCE_PRESET"))
                .andExpect(jsonPath("$.questions[0].content").value(ExperienceQuestion.ROLE.content()))
                .andExpect(jsonPath("$.questions[0].current").value(true))
                .andExpect(jsonPath("$.questions[0].attempts.length()").value(0));
    }

    @Test
    @DisplayName("이력서 기반이나 알 수 없는 시작 방식은 아직 받지 않는다")
    void rejectsUnsupportedStartMode() throws Exception {
        for (String body : List.of("{\"startMode\":\"RESUME\"}", "{\"startMode\":\"NOPE\"}", "{}")) {
            mockMvc.perform(write(post("/api/practices"), me).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_START_MODE"));
        }
    }

    @Test
    @DisplayName("목록에는 본인의 삭제되지 않은 연습만 최신순으로 나온다")
    void listsOnlyOwnPractices() throws Exception {
        long first = createPractice(me);
        long second = createPractice(me);
        createPractice(other);

        mockMvc.perform(get("/api/practices").cookie(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.items[1].id").value(first))
                .andExpect(jsonPath("$.items[0].questionCount").value(1))
                .andExpect(jsonPath("$.items[0].canProceed").value(true));
    }

    @Test
    @DisplayName("다른 사용자의 연습과 질문은 없는 것처럼 404로 응답한다")
    void hidesOtherUsersPractice() throws Exception {
        long practiceId = createPractice(me);
        long questionId = currentQuestionId(practiceId);

        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRACTICE_NOT_FOUND"));
        mockMvc.perform(write(post("/api/practices/{id}/complete", practiceId), other))
                .andExpect(status().isNotFound());
        mockMvc.perform(write(delete("/api/practices/{id}", practiceId), other))
                .andExpect(status().isNotFound());
        mockMvc.perform(submit(questionId, UUID.randomUUID(), "답변", other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
        assertThat(countAnswers(practiceId)).isZero();
    }

    @Test
    @DisplayName("답변을 제출하면 시도 1·버전 1과 대기 중인 피드백 요청이 함께 저장된다")
    void submitsAnswerWithPendingFeedbackRequest() throws Exception {
        long practiceId = createPractice(me);
        long questionId = currentQuestionId(practiceId);

        mockMvc.perform(submit(questionId, UUID.randomUUID(), "  예약 기능을 맡았습니다.  ", me))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNo").value(1))
                .andExpect(jsonPath("$.currentRevision.revisionNo").value(1))
                .andExpect(jsonPath("$.currentRevision.content").value("예약 기능을 맡았습니다."))
                .andExpect(jsonPath("$.feedback").isEmpty())
                .andExpect(jsonPath("$.feedbackRequest.status").value("PENDING"));

        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions[0].attempts.length()").value(1))
                .andExpect(jsonPath("$.questions[0].attempts[0].feedbackRequest.status").value("PENDING"));
        assertThat(countRequests(practiceId, "PENDING")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 질문에 다시 답하면 새 시도가 된다")
    void reanswerCreatesNewAttempt() throws Exception {
        long practiceId = createPractice(me);
        long questionId = currentQuestionId(practiceId);

        mockMvc.perform(submit(questionId, UUID.randomUUID(), "첫 답변", me)).andExpect(status().isCreated());
        mockMvc.perform(submit(questionId, UUID.randomUUID(), "다시 답변", me))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNo").value(2));

        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions[0].attempts[0].attemptNo").value(1))
                .andExpect(jsonPath("$.questions[0].attempts[1].attemptNo").value(2))
                .andExpect(jsonPath("$.questions[0].attempts[1].currentRevision.content").value("다시 답변"));
        assertThat(countRequests(practiceId, "PENDING")).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 제출을 다시 보내면 새로 만들지 않고 처음 결과를 200으로 돌려준다")
    void resubmissionIsIdempotent() throws Exception {
        long practiceId = createPractice(me);
        long questionId = currentQuestionId(practiceId);
        UUID clientRequestId = UUID.randomUUID();

        String first = mockMvc.perform(submit(questionId, clientRequestId, "답변", me))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(submit(questionId, clientRequestId, "답변", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((Integer) JsonPath.read(first, "$.id")));

        assertThat(countAnswers(practiceId)).isEqualTo(1);
        assertThat(countRequests(practiceId, "PENDING")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 제출이 동시에 여러 번 들어와도 시도와 요청은 하나만 생긴다")
    void concurrentResubmissionCreatesOneAttempt() throws Exception {
        long practiceId = createPractice(me);
        long questionId = currentQuestionId(practiceId);
        UUID clientRequestId = UUID.randomUUID();
        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> send = () -> {
            start.await();
            return mockMvc.perform(submit(questionId, clientRequestId, "답변", me)).andReturn().getResponse()
                    .getStatus();
        };

        List<Integer> statuses;
        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            List<Future<Integer>> futures = IntStream.range(0, requests).mapToObj(i -> executor.submit(send)).toList();
            start.countDown();
            statuses = futures.stream().map(PracticeApiTest::await).toList();
        }

        assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 200);
        assertThat(countAnswers(practiceId)).isEqualTo(1);
        assertThat(countRequests(practiceId, "PENDING")).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 답변이나 3,000자를 넘는 답변은 저장하지 않는다")
    void rejectsInvalidContent() throws Exception {
        long practiceId = createPractice(me);
        long questionId = currentQuestionId(practiceId);

        for (String content : List.of("", "   \n ", "가".repeat(3_001))) {
            mockMvc.perform(submit(questionId, UUID.randomUUID(), content, me))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ANSWER_CONTENT"));
        }
        mockMvc.perform(submit(questionId, UUID.randomUUID(), "가".repeat(3_000), me))
                .andExpect(status().isCreated());
        mockMvc.perform(write(post("/api/questions/{id}/answers", questionId), me).content("{\"content\":\"답변\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("현재 질문이 아닌 질문에는 답할 수 없다")
    void rejectsAnswerToPastQuestion() throws Exception {
        long practiceId = createPractice(me);
        long firstQuestionId = currentQuestionId(practiceId);
        // 넘기기·꼬리질문은 4단계에서 만든다. 여기서는 다음 질문이 생긴 상황을 직접 만든다.
        jdbcClient.sql("""
                        insert into questions (practice_id, order_no, content, source_kind, preset_key)
                        values (:practiceId, 2, '다음 질문', 'EXPERIENCE_PRESET', 'hardest')
                        """)
                .param("practiceId", practiceId)
                .update();

        mockMvc.perform(submit(firstQuestionId, UUID.randomUUID(), "답변", me))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CURRENT_QUESTION"));
    }

    @Test
    @DisplayName("연습을 마치면 진행이 막히고, 다시 마쳐도 완료 시각이 바뀌지 않는다")
    void completeIsIdempotentAndBlocksProgress() throws Exception {
        long practiceId = createPractice(me);
        long questionId = currentQuestionId(practiceId);
        mockMvc.perform(submit(questionId, UUID.randomUUID(), "답변", me)).andExpect(status().isCreated());

        String completed = mockMvc.perform(write(post("/api/practices/{id}/complete", practiceId), me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.canProceed").value(false))
                .andExpect(jsonPath("$.readOnlyReason").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        String completedAt = JsonPath.read(completed, "$.completedAt");

        mockMvc.perform(write(post("/api/practices/{id}/complete", practiceId), me))
                .andExpect(jsonPath("$.completedAt").value(completedAt));
        mockMvc.perform(submit(questionId, UUID.randomUUID(), "또 답변", me))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRACTICE_COMPLETED"));
        assertThat(countRequests(practiceId, "PENDING")).as("완료 전에 접수한 피드백 요청은 그대로 둔다").isEqualTo(1);
    }

    @Test
    @DisplayName("연습을 삭제하면 조회·답변이 막히고 대기 중인 AI 요청은 버려진다")
    void deleteHidesPracticeAndDiscardsPendingRequests() throws Exception {
        long practiceId = createPractice(me);
        long questionId = currentQuestionId(practiceId);
        mockMvc.perform(submit(questionId, UUID.randomUUID(), "답변", me)).andExpect(status().isCreated());

        mockMvc.perform(write(delete("/api/practices/{id}", practiceId), me)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/practices").cookie(me)).andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(submit(questionId, UUID.randomUUID(), "답변", me)).andExpect(status().isNotFound());
        mockMvc.perform(write(delete("/api/practices/{id}", practiceId), me)).andExpect(status().isNotFound());
        assertThat(jdbcClient.sql("select discard_reason from ai_requests where practice_id = :id")
                .param("id", practiceId).query(String.class).single()).isEqualTo("PRACTICE_DELETED");
    }

    @Test
    @DisplayName("상태를 바꾸는 연습 API도 다른 출처의 요청은 거부한다")
    void rejectsCrossOriginWrites() throws Exception {
        mockMvc.perform(post("/api/practices").cookie(me).contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "http://evil.example").content("{\"startMode\":\"EXPERIENCE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/practices")).andExpect(status().isUnauthorized());
    }

    private long createPractice(Cookie login) throws Exception {
        String body = mockMvc.perform(write(post("/api/practices"), login).content("{\"startMode\":\"EXPERIENCE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long currentQuestionId(long practiceId) {
        return jdbcClient.sql("select id from questions where practice_id = :id order by order_no desc limit 1")
                .param("id", practiceId).query(Long.class).single();
    }

    private MockHttpServletRequestBuilder submit(long questionId, UUID clientRequestId, String content,
                                                 Cookie login) {
        String body = "{\"clientRequestId\":\"" + clientRequestId + "\",\"content\":" + json(content) + "}";
        return write(post("/api/questions/{id}/answers", questionId), login).content(body);
    }

    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder, Cookie login) {
        return builder.cookie(login).header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON);
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private int countAnswers(long practiceId) {
        return jdbcClient.sql("""
                        select count(*) from answers a join questions q on q.id = a.question_id
                        where q.practice_id = :id
                        """)
                .param("id", practiceId).query(Integer.class).single();
    }

    private int countRequests(long practiceId, String status) {
        return jdbcClient.sql("select count(*) from ai_requests where practice_id = :id and status = :status")
                .param("id", practiceId).param("status", status).query(Integer.class).single();
    }

    private static Integer await(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
