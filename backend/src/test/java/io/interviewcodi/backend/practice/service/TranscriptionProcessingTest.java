package io.interviewcodi.backend.practice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.interviewcodi.backend.TestcontainersConfiguration;
import io.interviewcodi.backend.auth.TestLogin;
import io.interviewcodi.backend.practice.client.SpeechToTextClient;
import io.interviewcodi.backend.practice.client.SpeechToTextClient.AudioFile;
import io.interviewcodi.backend.practice.client.SpeechToTextClient.Transcription;
import io.interviewcodi.backend.practice.client.SpeechToTextException;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 녹음을 글로 옮기는 요청(TRANSCRIBE)의 처리. 워커 스케줄은 꺼 두고 {@link AiRequestProcessingService}를 직접 부른다.
 * 피드백 생성기는 가짜를 그대로 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TranscriptionProcessingTest {

    private static final String ORIGIN = "http://localhost";
    private static final AtomicLong NEXT_KAKAO_USER_ID = new AtomicLong(7_000_000L);
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0x02, 0x03, 0x04};
    private static final String TEXT = "레디스로 인기 게시글 목록을 캐시했습니다.";

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
    private SpeechToTextClient speechToTextClient;

    private Cookie me;

    @BeforeEach
    void setUp() {
        // 다른 테스트 클래스가 남긴 대기 요청을 이 테스트에서 처리하지 않도록 정리한다.
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
    @DisplayName("인식 문구가 버전 1이 되고, 같은 트랜잭션에서 그 버전의 피드백 요청이 만들어진다")
    void transcriptionBecomesFirstRevisionWithFeedbackRequest() throws Exception {
        when(speechToTextClient.transcribe(any())).thenReturn(new Transcription("  " + TEXT + " ", 1200, 5.0));
        long practiceId = createPractice();
        Uploaded uploaded = upload(practiceId);

        assertThat(processing.processNext()).isTrue();

        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions[0].attempts[0].inputMode").value("VOICE"))
                .andExpect(jsonPath("$.questions[0].attempts[0].transcriptionRequest.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.questions[0].attempts[0].currentRevision.revisionNo").value(1))
                .andExpect(jsonPath("$.questions[0].attempts[0].currentRevision.content").value(TEXT))
                .andExpect(jsonPath("$.questions[0].attempts[0].feedbackRequest.status").value("PENDING"));
        ArgumentCaptor<AudioFile> audio = ArgumentCaptor.forClass(AudioFile.class);
        verify(speechToTextClient).transcribe(audio.capture());
        assertThat(audio.getValue().bytes()).isEqualTo(WEBM);
        assertThat(audio.getValue().filename()).isEqualTo("answer.webm");
        assertThat(audio.getValue().contentType()).isEqualTo("audio/webm");

        assertThat(processing.processNext()).as("이어서 피드백 요청을 처리한다").isTrue();
        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions[0].attempts[0].feedback.observation").isString());
        assertThat(requestColumn(uploaded.requestId(), "status")).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("인식 결과가 비어 있으면 다시 시도하지 않고 FAILED로 끝내며 버전을 만들지 않는다")
    void emptyTranscriptFailsWithoutRetry() throws Exception {
        when(speechToTextClient.transcribe(any())).thenThrow(new SpeechToTextException("EMPTY_TEXT", null));
        long practiceId = createPractice();
        Uploaded uploaded = upload(practiceId);

        processing.processNext();

        assertThat(requestColumn(uploaded.requestId(), "status")).isEqualTo("FAILED");
        assertThat(requestColumn(uploaded.requestId(), "last_error")).isEqualTo("EMPTY_TEXT");
        assertThat(countRevisions(uploaded.answerId())).isZero();
    }

    @Test
    @DisplayName("제공자 오류는 간격을 두고 다시 시도하고, 세 번째 실패면 FAILED로 끝난다")
    void providerErrorRetriesThenFails() throws Exception {
        when(speechToTextClient.transcribe(any())).thenThrow(new SpeechToTextException("HTTP_503", null));
        long practiceId = createPractice();
        Uploaded uploaded = upload(practiceId);

        processing.processNext();
        assertThat(requestColumn(uploaded.requestId(), "status")).isEqualTo("PENDING");
        makeAvailableNow(uploaded.requestId());
        processing.processNext();
        makeAvailableNow(uploaded.requestId());
        processing.processNext();

        assertThat(requestColumn(uploaded.requestId(), "status")).isEqualTo("FAILED");
        assertThat(requestColumn(uploaded.requestId(), "attempt_count")).isEqualTo("3");
        assertThat(countRevisions(uploaded.answerId())).isZero();
    }

    @Test
    @DisplayName("인식하는 동안 연습을 지우면 결과를 버린다")
    void discardsResultWhenPracticeDeletedDuringTranscription() throws Exception {
        long practiceId = createPractice();
        Uploaded uploaded = upload(practiceId);
        when(speechToTextClient.transcribe(any())).thenAnswer(invocation -> {
            mockMvc.perform(write(delete("/api/practices/{id}", practiceId))).andExpect(status().isNoContent());
            return new Transcription(TEXT, 1000, null);
        });

        processing.processNext();

        assertThat(requestColumn(uploaded.requestId(), "status")).isEqualTo("DISCARDED");
        assertThat(requestColumn(uploaded.requestId(), "discard_reason")).isEqualTo("PRACTICE_DELETED");
        assertThat(countRevisions(uploaded.answerId())).isZero();
    }

    @Test
    @DisplayName("인식하는 동안 연습을 마쳐도 제출한 답변은 글로 옮겨 기록에 남긴다")
    void keepsResultWhenPracticeCompletedDuringTranscription() throws Exception {
        long practiceId = createPractice();
        Uploaded uploaded = upload(practiceId);
        when(speechToTextClient.transcribe(any())).thenAnswer(invocation -> {
            mockMvc.perform(write(post("/api/practices/{id}/complete", practiceId))).andExpect(status().isOk());
            return new Transcription(TEXT, 1000, null);
        });

        processing.processNext();

        assertThat(requestColumn(uploaded.requestId(), "status")).isEqualTo("SUCCEEDED");
        assertThat(countRevisions(uploaded.answerId())).isEqualTo(1);
    }

    @Test
    @DisplayName("실패한 녹음은 다시 글로 옮길 수 있고, 처리 중이면 같은 요청을 돌려준다")
    void retriesFailedTranscription() throws Exception {
        when(speechToTextClient.transcribe(any())).thenThrow(new SpeechToTextException("EMPTY_TEXT", null));
        long practiceId = createPractice();
        Uploaded uploaded = upload(practiceId);
        processing.processNext();

        String body = mockMvc.perform(write(post("/api/recordings/{id}/transcriptions", uploaded.recordingId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long retryId = ((Number) JsonPath.read(body, "$.id")).longValue();
        mockMvc.perform(write(post("/api/recordings/{id}/transcriptions", uploaded.recordingId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(retryId));

        doReturn(new Transcription(TEXT, 1000, null)).when(speechToTextClient).transcribe(any());
        processing.processNext();
        assertThat(countRevisions(uploaded.answerId())).isEqualTo(1);
        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(jsonPath("$.questions[0].attempts[0].transcriptionRequest.id").value(retryId))
                .andExpect(jsonPath("$.questions[0].attempts[0].transcriptionRequest.status").value("SUCCEEDED"));
    }

    @Test
    @DisplayName("다른 사용자의 녹음과 마친 연습의 녹음은 다시 글로 옮길 수 없다")
    void rejectsInvalidTranscriptionRetries() throws Exception {
        when(speechToTextClient.transcribe(any())).thenThrow(new SpeechToTextException("EMPTY_TEXT", null));
        long practiceId = createPractice();
        Uploaded uploaded = upload(practiceId);
        processing.processNext();

        mockMvc.perform(post("/api/recordings/{id}/transcriptions", uploaded.recordingId())
                        .cookie(login()).header("Origin", ORIGIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING_NOT_FOUND"));
        mockMvc.perform(write(post("/api/practices/{id}/complete", practiceId))).andExpect(status().isOk());
        mockMvc.perform(write(post("/api/recordings/{id}/transcriptions", uploaded.recordingId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRACTICE_COMPLETED"));
    }

    private record Uploaded(long answerId, long recordingId, long requestId) {
    }

    private Cookie login() {
        return TestLogin.sessionCookie(sessionRepository,
                userAccountService.findOrCreateByKakaoUserId(NEXT_KAKAO_USER_ID.incrementAndGet()));
    }

    private long createPractice() throws Exception {
        String body = mockMvc.perform(write(post("/api/practices"))
                        .contentType("application/json").content("{\"startMode\":\"EXPERIENCE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private Uploaded upload(long practiceId) throws Exception {
        long questionId = jdbcClient.sql("select id from questions where practice_id = :id order by order_no desc limit 1")
                .param("id", practiceId).query(Long.class).single();
        String body = mockMvc.perform(multipart("/api/questions/{id}/voice-answers", questionId)
                        .file(new MockMultipartFile("audio", "answer.webm", "audio/webm;codecs=opus", WEBM))
                        .param("clientRequestId", UUID.randomUUID().toString())
                        .param("durationMs", "5000")
                        .cookie(me)
                        .header("Origin", ORIGIN))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Uploaded(((Number) JsonPath.read(body, "$.id")).longValue(),
                ((Number) JsonPath.read(body, "$.recording.id")).longValue(),
                ((Number) JsonPath.read(body, "$.transcriptionRequest.id")).longValue());
    }

    private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder) {
        return builder.cookie(me).header("Origin", ORIGIN);
    }

    private void makeAvailableNow(long requestId) {
        jdbcClient.sql("update ai_requests set available_at = now() where id = :id").param("id", requestId).update();
    }

    private String requestColumn(long requestId, String column) {
        return jdbcClient.sql("select " + column + "::text from ai_requests where id = :id")
                .param("id", requestId).query(String.class).single();
    }

    private int countRevisions(long answerId) {
        return jdbcClient.sql("select count(*) from answer_revisions where answer_id = :id")
                .param("id", answerId).query(Integer.class).single();
    }
}
