package io.interviewcodi.backend.practice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.interviewcodi.backend.TestcontainersConfiguration;
import io.interviewcodi.backend.auth.TestLogin;
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
 * 문구 교정은 같은 답변 시도의 새 버전이다. 재답변(새 시도)으로 세지 않고, 낙관적 충돌로 동시 교정을 막는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnswerRevisionApiTest {

    private static final String ORIGIN = "http://localhost";
    private static final AtomicLong NEXT_KAKAO_USER_ID = new AtomicLong(4_000_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private SessionRepository<?> sessionRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private Cookie me;

    @BeforeEach
    void logIn() {
        me = login();
    }

    @Test
    @DisplayName("교정하면 같은 시도에 버전 2가 생기고, 새 버전의 피드백 요청이 만들어진다")
    void revisionCreatesNextVersionWithNewFeedbackRequest() throws Exception {
        long practiceId = createPractice();
        Submitted submitted = submit(practiceId, "예약 기능 맡았습니다");

        mockMvc.perform(revise(submitted.answerId(), 1, "예약 기능을 맡았습니다."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(submitted.answerId()))
                .andExpect(jsonPath("$.attemptNo").value(1))
                .andExpect(jsonPath("$.currentRevision.revisionNo").value(2))
                .andExpect(jsonPath("$.currentRevision.content").value("예약 기능을 맡았습니다."))
                .andExpect(jsonPath("$.feedbackRequest.status").value("PENDING"));

        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions[0].attempts.length()").value(1))
                .andExpect(jsonPath("$.questions[0].attempts[0].currentRevision.revisionNo").value(2));
        assertThat(requestStatus(submitted.requestId())).as("교정 전 버전의 대기 요청은 버린다")
                .isEqualTo("DISCARDED/SUPERSEDED");
        assertThat(countActiveRequests(practiceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("보고 있던 버전이 현재 버전이 아니면 409로 알리고 저장하지 않는다")
    void staleBaseRevisionConflicts() throws Exception {
        long practiceId = createPractice();
        Submitted submitted = submit(practiceId, "처음 답변");
        mockMvc.perform(revise(submitted.answerId(), 1, "첫 교정")).andExpect(status().isCreated());

        mockMvc.perform(revise(submitted.answerId(), 1, "다른 탭에서 한 교정"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));

        assertThat(countRevisions(submitted.answerId())).isEqualTo(2);
    }

    @Test
    @DisplayName("본문이 현재 버전과 같으면 새 버전과 요청을 만들지 않는다")
    void unchangedContentIsNoop() throws Exception {
        long practiceId = createPractice();
        Submitted submitted = submit(practiceId, "같은 답변");

        mockMvc.perform(revise(submitted.answerId(), 1, "  같은 답변 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRevision.revisionNo").value(1))
                .andExpect(jsonPath("$.feedbackRequest.id").value(submitted.requestId()));

        assertThat(countRevisions(submitted.answerId())).isEqualTo(1);
        assertThat(requestStatus(submitted.requestId())).isEqualTo("PENDING/");
    }

    @Test
    @DisplayName("같은 버전에서 동시에 교정해도 하나만 저장되고 나머지는 409를 받는다")
    void concurrentRevisionsFromSameBase() throws Exception {
        long practiceId = createPractice();
        Submitted submitted = submit(practiceId, "처음 답변");
        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = IntStream.range(0, requests)
                .<Callable<Integer>>mapToObj(i -> () -> {
                    start.await();
                    return mockMvc.perform(revise(submitted.answerId(), 1, "교정 " + i)).andReturn().getResponse()
                            .getStatus();
                })
                .toList();

        List<Integer> statuses;
        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            List<Future<Integer>> futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            statuses = futures.stream().map(AnswerRevisionApiTest::await).toList();
        }

        assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 409);
        assertThat(countRevisions(submitted.answerId())).isEqualTo(2);
        assertThat(countActiveRequests(practiceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("마친 연습, 다른 사용자의 답변, 잘못된 입력은 교정할 수 없다")
    void rejectsInvalidRevisions() throws Exception {
        long practiceId = createPractice();
        Submitted submitted = submit(practiceId, "답변");

        mockMvc.perform(revise(submitted.answerId(), 1, " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ANSWER_CONTENT"));
        mockMvc.perform(write(post("/api/answers/{id}/revisions", submitted.answerId()), me)
                        .content("{\"content\":\"교정\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(write(post("/api/answers/{id}/revisions", submitted.answerId()), login())
                        .content("{\"baseRevisionNo\":1,\"content\":\"교정\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANSWER_NOT_FOUND"));

        mockMvc.perform(write(post("/api/practices/{id}/complete", practiceId), me)).andExpect(status().isOk());
        mockMvc.perform(revise(submitted.answerId(), 1, "교정"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRACTICE_COMPLETED"));
        assertThat(countRevisions(submitted.answerId())).isEqualTo(1);
    }

    private record Submitted(long answerId, long revisionId, long requestId) {
    }

    private Cookie login() {
        return TestLogin.sessionCookie(sessionRepository,
                userAccountService.findOrCreateByKakaoUserId(NEXT_KAKAO_USER_ID.incrementAndGet()));
    }

    private long createPractice() throws Exception {
        String body = mockMvc.perform(write(post("/api/practices"), me).content("{\"startMode\":\"EXPERIENCE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Submitted submit(long practiceId, String content) throws Exception {
        long questionId = jdbcClient.sql("select id from questions where practice_id = :id order by order_no desc limit 1")
                .param("id", practiceId).query(Long.class).single();
        String body = mockMvc.perform(write(post("/api/questions/{id}/answers", questionId), me)
                        .content("{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Submitted(((Number) JsonPath.read(body, "$.id")).longValue(),
                ((Number) JsonPath.read(body, "$.currentRevision.id")).longValue(),
                ((Number) JsonPath.read(body, "$.feedbackRequest.id")).longValue());
    }

    private MockHttpServletRequestBuilder revise(long answerId, int baseRevisionNo, String content) {
        return write(post("/api/answers/{id}/revisions", answerId), me)
                .content("{\"baseRevisionNo\":" + baseRevisionNo + ",\"content\":\"" + content + "\"}");
    }

    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder, Cookie login) {
        return builder.cookie(login).header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON);
    }

    private int countRevisions(long answerId) {
        return jdbcClient.sql("select count(*) from answer_revisions where answer_id = :id")
                .param("id", answerId).query(Integer.class).single();
    }

    private int countActiveRequests(long practiceId) {
        return jdbcClient.sql("""
                        select count(*) from ai_requests where practice_id = :id and status in ('PENDING', 'RUNNING')
                        """)
                .param("id", practiceId).query(Integer.class).single();
    }

    /** {@code 상태/버린 사유}. */
    private String requestStatus(long requestId) {
        return jdbcClient.sql("select status || '/' || coalesce(discard_reason, '') from ai_requests where id = :id")
                .param("id", requestId).query(String.class).single();
    }

    private static Integer await(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
