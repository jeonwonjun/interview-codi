package io.interviewcodi.backend.practice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.interviewcodi.backend.TestcontainersConfiguration;
import io.interviewcodi.backend.auth.TestLogin;
import io.interviewcodi.backend.practice.client.FollowUpQuestionGenerator;
import io.interviewcodi.backend.practice.client.FollowUpQuestionGenerator.FollowUpInput;
import io.interviewcodi.backend.practice.client.FollowUpQuestionGenerator.GeneratedQuestion;
import io.interviewcodi.backend.practice.domain.ExperienceQuestion;
import io.interviewcodi.backend.user.service.UserAccountService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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
 * 꼬리질문(AI, 비동기)과 질문 넘기기(고정 질문, 즉시). 꼬리질문 생성 도중에 마치기·넘기기·교정이 끼는 순서를
 * 직접 만들어, 무의미해진 결과가 연습에 새 질문으로 들어가지 않는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FollowUpAndSkipTest {

    private static final String ORIGIN = "http://localhost";
    private static final AtomicLong NEXT_KAKAO_USER_ID = new AtomicLong(5_000_000L);
    private static final GeneratedQuestion FOLLOW_UP = new GeneratedQuestion("그 검증을 어디에 두셨나요?", "test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiRequestProcessingService processing;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private SessionRepository<?> sessionRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private FollowUpQuestionGenerator generator;

    private Cookie me;

    @BeforeEach
    void setUp() {
        // 다른 테스트 클래스가 남긴 대기 요청을 처리하지 않도록 정리한다.
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
    @DisplayName("꼬리질문을 요청하면 답변을 근거로 만든 질문이 다음 질문이 된다")
    void followUpBecomesNextQuestion() throws Exception {
        when(generator.generate(any())).thenReturn(FOLLOW_UP);
        long practiceId = createPractice();
        Submitted answer = submitToCurrent(practiceId, "입력값 검증을 맡았습니다.");
        drain();

        long requestId = requestFollowUp(practiceId, answer.revisionId(), 202);
        assertThat(requestFollowUp(practiceId, answer.revisionId(), 200)).as("처리 중이면 같은 요청").isEqualTo(requestId);
        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.followUpRequest.id").value(requestId));

        drain();

        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].current").value(false))
                .andExpect(jsonPath("$.questions[1].sourceKind").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.questions[1].content").value(FOLLOW_UP.content()))
                .andExpect(jsonPath("$.questions[1].current").value(true))
                .andExpect(jsonPath("$.followUpRequest").isEmpty());
        mockMvc.perform(get("/api/ai-requests/{id}", requestId).cookie(me))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.questionId").isNumber());
        ArgumentCaptor<FollowUpInput> input = ArgumentCaptor.forClass(FollowUpInput.class);
        verify(generator).generate(input.capture());
        assertThat(input.getValue().answer()).isEqualTo("입력값 검증을 맡았습니다.");
        assertThat(input.getValue().askedQuestions()).containsExactly(ExperienceQuestion.ROLE.content());

        submitToCurrent(practiceId, "서비스 계층에 두었습니다.");
    }

    @Test
    @DisplayName("꼬리질문 생성 중에 연습을 마치면 새 질문을 넣지 않는다")
    void discardsWhenCompletedDuringGeneration() throws Exception {
        long practiceId = createPractice();
        Submitted answer = submitToCurrent(practiceId, "답변");
        drain();
        long requestId = requestFollowUp(practiceId, answer.revisionId(), 202);
        when(generator.generate(any())).thenAnswer(invocation -> {
            perform(post("/api/practices/{id}/complete", practiceId), "", 200);
            return FOLLOW_UP;
        });

        drain();

        assertThat(requestStatus(requestId)).isEqualTo("DISCARDED/PRACTICE_COMPLETED");
        assertThat(countQuestions(practiceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("대기 중인 꼬리질문 요청은 연습을 마치는 즉시 버린다")
    void completingDiscardsPendingFollowUp() throws Exception {
        long practiceId = createPractice();
        Submitted answer = submitToCurrent(practiceId, "답변");
        long requestId = requestFollowUp(practiceId, answer.revisionId(), 202);

        perform(post("/api/practices/{id}/complete", practiceId), "", 200);

        assertThat(requestStatus(requestId)).isEqualTo("DISCARDED/PRACTICE_COMPLETED");
    }

    @Test
    @DisplayName("꼬리질문 생성 중에 질문을 넘기면 결과를 버리고 넘기기로 받은 고정 질문이 남는다")
    void discardsWhenSkippedDuringGeneration() throws Exception {
        long practiceId = createPractice();
        Submitted answer = submitToCurrent(practiceId, "답변");
        drain();
        long requestId = requestFollowUp(practiceId, answer.revisionId(), 202);
        when(generator.generate(any())).thenAnswer(invocation -> {
            perform(post("/api/questions/{id}/skip", answer.questionId()), "", 201);
            return FOLLOW_UP;
        });

        drain();

        assertThat(requestStatus(requestId)).isEqualTo("DISCARDED/QUESTION_CHANGED");
        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].skipped").value(false))
                .andExpect(jsonPath("$.questions[1].content").value(ExperienceQuestion.HARDEST.content()));
    }

    @Test
    @DisplayName("꼬리질문 생성 중에 답변이 교정되면 교정 전 답변에 근거한 질문은 버린다")
    void discardsWhenRevisedDuringGeneration() throws Exception {
        long practiceId = createPractice();
        Submitted answer = submitToCurrent(practiceId, "처음 답변");
        drain();
        long requestId = requestFollowUp(practiceId, answer.revisionId(), 202);
        when(generator.generate(any())).thenAnswer(invocation -> {
            perform(post("/api/answers/{id}/revisions", answer.answerId()),
                    "{\"baseRevisionNo\":1,\"content\":\"교정한 답변\"}", 201);
            return FOLLOW_UP;
        });

        processing.processNext();

        assertThat(requestStatus(requestId)).isEqualTo("DISCARDED/SUPERSEDED");
        assertThat(countQuestions(practiceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 질문의 현재 버전이 아니거나 남의 연습이면 꼬리질문을 요청할 수 없다")
    void rejectsInvalidFollowUpRequests() throws Exception {
        long practiceId = createPractice();
        Submitted answer = submitToCurrent(practiceId, "답변");
        long otherPracticeId = createPractice();

        perform(post("/api/practices/{id}/follow-up-requests", otherPracticeId),
                "{\"answerRevisionId\":" + answer.revisionId() + "}", 404);
        mockMvc.perform(write(post("/api/practices/{id}/follow-up-requests", practiceId), login())
                        .content("{\"answerRevisionId\":" + answer.revisionId() + "}"))
                .andExpect(status().isNotFound());
        perform(post("/api/practices/{id}/follow-up-requests", practiceId), "{}", 400);

        perform(post("/api/answers/{id}/revisions", answer.answerId()), "{\"baseRevisionNo\":1,\"content\":\"교정\"}",
                201);
        mockMvc.perform(write(post("/api/practices/{id}/follow-up-requests", practiceId), me)
                        .content("{\"answerRevisionId\":" + answer.revisionId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_LATEST_REVISION"));

        long revision2 = jdbcClient.sql("select id from answer_revisions where answer_id = :a and revision_no = 2")
                .param("a", answer.answerId()).query(Long.class).single();
        perform(post("/api/questions/{id}/skip", answer.questionId()), "", 201);
        mockMvc.perform(write(post("/api/practices/{id}/follow-up-requests", practiceId), me)
                        .content("{\"answerRevisionId\":" + revision2 + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CURRENT_QUESTION"));
    }

    @Test
    @DisplayName("답하지 않은 질문을 넘기면 넘긴 질문으로 표시되고, 고정 질문을 다 쓰면 더 넘길 수 없다")
    void skipsThroughPresetQuestions() throws Exception {
        long practiceId = createPractice();

        for (int i = 1; i < ExperienceQuestion.values().length; i++) {
            long current = currentQuestionId(practiceId);
            String next = perform(post("/api/questions/{id}/skip", current), "", 201);
            assertThat((String) JsonPath.read(next, "$.content")).isEqualTo(ExperienceQuestion.values()[i].content());
            assertThat((Integer) JsonPath.read(next, "$.orderNo")).isEqualTo(i + 1);
        }
        long last = currentQuestionId(practiceId);
        mockMvc.perform(write(post("/api/questions/{id}/skip", last), me))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_MORE_QUESTIONS"));

        long first = jdbcClient.sql("select id from questions where practice_id = :id and order_no = 1")
                .param("id", practiceId).query(Long.class).single();
        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions.length()").value(ExperienceQuestion.values().length))
                .andExpect(jsonPath("$.questions[0].skipped").value(true))
                .andExpect(jsonPath("$.questions[4].skipped").value(false))
                .andExpect(jsonPath("$.questions[4].current").value(true));
        mockMvc.perform(write(post("/api/questions/{id}/answers", first), me)
                        .content("{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"content\":\"답변\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CURRENT_QUESTION"));
    }

    private record Submitted(long questionId, long answerId, long revisionId) {
    }

    private void drain() {
        while (processing.processNext()) {
            // 큐가 빌 때까지 처리한다.
        }
    }

    private Cookie login() {
        return TestLogin.sessionCookie(sessionRepository,
                userAccountService.findOrCreateByKakaoUserId(NEXT_KAKAO_USER_ID.incrementAndGet()));
    }

    private long createPractice() throws Exception {
        String body = perform(post("/api/practices"), "{\"startMode\":\"EXPERIENCE\"}", 201);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long currentQuestionId(long practiceId) {
        return jdbcClient.sql("select id from questions where practice_id = :id order by order_no desc limit 1")
                .param("id", practiceId).query(Long.class).single();
    }

    private Submitted submitToCurrent(long practiceId, String content) throws Exception {
        long questionId = currentQuestionId(practiceId);
        String body = perform(post("/api/questions/{id}/answers", questionId),
                "{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"content\":\"" + content + "\"}", 201);
        return new Submitted(questionId, ((Number) JsonPath.read(body, "$.id")).longValue(),
                ((Number) JsonPath.read(body, "$.currentRevision.id")).longValue());
    }

    private long requestFollowUp(long practiceId, long revisionId, int expectedStatus) throws Exception {
        String body = perform(post("/api/practices/{id}/follow-up-requests", practiceId),
                "{\"answerRevisionId\":" + revisionId + "}", expectedStatus);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String perform(MockHttpServletRequestBuilder builder, String body, int expectedStatus) throws Exception {
        return mockMvc.perform(write(builder, me).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder, Cookie login) {
        return builder.cookie(login).header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON);
    }

    private int countQuestions(long practiceId) {
        return jdbcClient.sql("select count(*) from questions where practice_id = :id")
                .param("id", practiceId).query(Integer.class).single();
    }

    private String requestStatus(long requestId) {
        return jdbcClient.sql("select status || '/' || coalesce(discard_reason, '') from ai_requests where id = :id")
                .param("id", requestId).query(String.class).single();
    }
}
