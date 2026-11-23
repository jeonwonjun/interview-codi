package io.interviewcodi.backend.practice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.interviewcodi.backend.TestcontainersConfiguration;
import io.interviewcodi.backend.auth.TestLogin;
import io.interviewcodi.backend.user.service.UserAccountService;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.FileSystemUtils;

/**
 * 음성 답변 올리기와 녹음 재생. 인식은 다음 단계(워커)라 여기서는 녹음·인식 요청이 만들어지는 데까지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VoiceAnswerApiTest {

    private static final String ORIGIN = "http://localhost";
    private static final AtomicLong NEXT_KAKAO_USER_ID = new AtomicLong(6_000_000L);
    // webm(EBML) 서명으로 시작하는 가짜 녹음. 내용은 확인하지 않는다.
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private SessionRepository<?> sessionRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Value("${practice.recording.local-dir}")
    private Path storageRoot;

    private long myUserId;
    private Cookie me;

    @BeforeEach
    void logIn() throws IOException {
        myUserId = userAccountService.findOrCreateByKakaoUserId(NEXT_KAKAO_USER_ID.incrementAndGet());
        me = TestLogin.sessionCookie(sessionRepository, myUserId);
        // 테스트 DB는 실행마다 새로 만들어 사용자 ID가 겹친다. 이전 실행이 같은 사용자 폴더에 남긴 파일을 지운다.
        FileSystemUtils.deleteRecursively(userDir());
    }

    @Test
    @DisplayName("음성 답변을 올리면 버전 없는 음성 시도와 녹음, 인식 요청이 만들어진다")
    void uploadCreatesVoiceAttemptWithTranscribeRequest() throws Exception {
        long practiceId = createPractice();
        long questionId = currentQuestionId(practiceId);

        String body = upload(questionId, UUID.randomUUID(), "audio/webm;codecs=opus", WEBM, 62_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNo").value(1))
                .andExpect(jsonPath("$.inputMode").value("VOICE"))
                .andExpect(jsonPath("$.currentRevision").isEmpty())
                .andExpect(jsonPath("$.feedbackRequest").isEmpty())
                .andExpect(jsonPath("$.recording.durationMs").value(62_000))
                .andExpect(jsonPath("$.transcriptionRequest.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long requestId = ((Number) JsonPath.read(body, "$.transcriptionRequest.id")).longValue();

        mockMvc.perform(get("/api/practices/{id}", practiceId).cookie(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].attempts[0].inputMode").value("VOICE"))
                .andExpect(jsonPath("$.questions[0].attempts[0].currentRevision").isEmpty())
                .andExpect(jsonPath("$.questions[0].attempts[0].transcriptionRequest.id").value(requestId));
        mockMvc.perform(get("/api/ai-requests/{id}", requestId).cookie(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("TRANSCRIBE"))
                .andExpect(jsonPath("$.answerRevisionId").isEmpty())
                .andExpect(jsonPath("$.recordingId").isNumber());
        assertThat(storedFiles()).hasSize(1);
        assertThat(recordingContentType(practiceId)).as("형식 매개변수는 떼고 저장한다").isEqualTo("audio/webm");
    }

    @Test
    @DisplayName("같은 제출을 다시 보내면 처음 결과를 200으로 돌려주고 파일을 더 쓰지 않는다")
    void resendReturnsFirstResult() throws Exception {
        long questionId = currentQuestionId(createPractice());
        UUID clientRequestId = UUID.randomUUID();
        String first = upload(questionId, clientRequestId, "audio/webm", WEBM, 5_000)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        upload(questionId, clientRequestId, "audio/webm", WEBM, 5_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(((Number) JsonPath.read(first, "$.id")).longValue()));

        assertThat(storedFiles()).hasSize(1);
    }

    @Test
    @DisplayName("오디오 서명이 맞지 않거나 길이가 잘못된 녹음은 저장하지 않는다")
    void rejectsInvalidRecording() throws Exception {
        long practiceId = createPractice();
        long questionId = currentQuestionId(practiceId);

        upload(questionId, UUID.randomUUID(), "audio/webm", "not audio".getBytes(), 5_000)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECORDING"));
        upload(questionId, UUID.randomUUID(), "text/plain", WEBM, 5_000)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECORDING"));
        upload(questionId, UUID.randomUUID(), "audio/webm", WEBM, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECORDING"));

        assertThat(countAnswers(practiceId)).isZero();
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    @DisplayName("저장할 수 없는 제출이면 먼저 쓴 파일을 지운다")
    void removesFileWhenAttemptIsRejected() throws Exception {
        long practiceId = createPractice();
        long questionId = currentQuestionId(practiceId);
        mockMvc.perform(write(post("/api/practices/{id}/complete", practiceId))).andExpect(status().isOk());

        upload(questionId, UUID.randomUUID(), "audio/webm", WEBM, 5_000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRACTICE_COMPLETED"));

        assertThat(countAnswers(practiceId)).isZero();
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    @DisplayName("글로 옮기는 중에는 다시 답하거나 고칠 수 없다")
    void blocksNewAttemptsWhileTranscribing() throws Exception {
        long practiceId = createPractice();
        long questionId = currentQuestionId(practiceId);
        String body = upload(questionId, UUID.randomUUID(), "audio/webm", WEBM, 5_000)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long answerId = ((Number) JsonPath.read(body, "$.id")).longValue();

        upload(questionId, UUID.randomUUID(), "audio/webm", WEBM, 5_000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSCRIPTION_IN_PROGRESS"));
        mockMvc.perform(write(post("/api/questions/{id}/answers", questionId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"content\":\"글로 답할게요\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSCRIPTION_IN_PROGRESS"));
        mockMvc.perform(write(post("/api/answers/{id}/revisions", answerId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRevisionNo\":1,\"content\":\"고친 문구\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSCRIPTION_IN_PROGRESS"));

        assertThat(countAnswers(practiceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("인식이 실패했으면 글로 다시 답할 수 있다")
    void allowsTextAnswerAfterTranscriptionFailed() throws Exception {
        long practiceId = createPractice();
        long questionId = currentQuestionId(practiceId);
        upload(questionId, UUID.randomUUID(), "audio/webm", WEBM, 5_000).andExpect(status().isCreated());
        jdbcClient.sql("""
                        update ai_requests set status = 'FAILED', finished_at = now()
                        where practice_id = :id and kind = 'TRANSCRIBE'
                        """)
                .param("id", practiceId)
                .update();

        mockMvc.perform(write(post("/api/questions/{id}/answers", questionId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"" + UUID.randomUUID() + "\",\"content\":\"글로 답할게요\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNo").value(2))
                .andExpect(jsonPath("$.inputMode").value("TEXT"));
    }

    @Test
    @DisplayName("본인 녹음만 재생할 수 있고, 위치 이동을 위한 부분 요청을 지원한다")
    void servesRecordingToOwnerOnly() throws Exception {
        long practiceId = createPractice();
        String body = upload(currentQuestionId(practiceId), UUID.randomUUID(), "audio/webm", WEBM, 5_000)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long answerId = ((Number) JsonPath.read(body, "$.id")).longValue();

        mockMvc.perform(get("/api/answers/{id}/recording", answerId).cookie(me))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/webm"))
                .andExpect(content().bytes(WEBM))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"));
        mockMvc.perform(get("/api/answers/{id}/recording", answerId).cookie(me).header(HttpHeaders.RANGE, "bytes=0-3"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-3/" + WEBM.length));

        Cookie other = TestLogin.sessionCookie(sessionRepository,
                userAccountService.findOrCreateByKakaoUserId(NEXT_KAKAO_USER_ID.incrementAndGet()));
        mockMvc.perform(get("/api/answers/{id}/recording", answerId).cookie(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING_NOT_FOUND"));
    }

    @Test
    @DisplayName("연습이나 녹음을 지우면 녹음을 재생할 수 없다")
    void hidesDeletedRecordings() throws Exception {
        long practiceId = createPractice();
        String body = upload(currentQuestionId(practiceId), UUID.randomUUID(), "audio/webm", WEBM, 5_000)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long answerId = ((Number) JsonPath.read(body, "$.id")).longValue();
        long secondPracticeId = createPractice();
        String second = upload(currentQuestionId(secondPracticeId), UUID.randomUUID(), "audio/webm", WEBM, 5_000)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long secondAnswerId = ((Number) JsonPath.read(second, "$.id")).longValue();

        mockMvc.perform(write(delete("/api/practices/{id}", practiceId))).andExpect(status().isNoContent());
        jdbcClient.sql("update recordings set deleted_at = now() where answer_id = :id")
                .param("id", secondAnswerId)
                .update();

        mockMvc.perform(get("/api/answers/{id}/recording", answerId).cookie(me))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/answers/{id}/recording", secondAnswerId).cookie(me))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/practices/{id}", secondPracticeId).cookie(me))
                .andExpect(jsonPath("$.questions[0].attempts[0].recording").isEmpty());
        assertThat(storedFiles()).as("논리적 삭제라 파일은 남는다").hasSize(2);
    }

    private ResultActions upload(long questionId, UUID clientRequestId, String contentType, byte[] audio,
                                 int durationMs) throws Exception {
        return mockMvc.perform(multipart("/api/questions/{id}/voice-answers", questionId)
                .file(new MockMultipartFile("audio", "answer.webm", contentType, audio))
                .param("clientRequestId", clientRequestId.toString())
                .param("durationMs", String.valueOf(durationMs))
                .cookie(me)
                .header("Origin", ORIGIN));
    }

    private long createPractice() throws Exception {
        String body = mockMvc.perform(write(post("/api/practices")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startMode\":\"EXPERIENCE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long currentQuestionId(long practiceId) {
        return jdbcClient.sql("select id from questions where practice_id = :id order by order_no desc limit 1")
                .param("id", practiceId).query(Long.class).single();
    }

    private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder) {
        return builder.cookie(me).header("Origin", ORIGIN);
    }

    private int countAnswers(long practiceId) {
        return jdbcClient.sql("""
                        select count(*) from answers a join questions q on q.id = a.question_id
                        where q.practice_id = :id
                        """)
                .param("id", practiceId).query(Integer.class).single();
    }

    private String recordingContentType(long practiceId) {
        return jdbcClient.sql("""
                        select rec.content_type from recordings rec
                        join answers a on a.id = rec.answer_id
                        join questions q on q.id = a.question_id
                        where q.practice_id = :id
                        """)
                .param("id", practiceId).query(String.class).single();
    }

    private Path userDir() {
        return storageRoot.resolve("recordings").resolve(String.valueOf(myUserId));
    }

    /** 이 사용자 폴더에 남은 녹음 파일(임시 파일 제외). */
    private List<Path> storedFiles() throws IOException {
        Path userDir = userDir();
        if (!Files.isDirectory(userDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(userDir)) {
            return files.filter(path -> !path.getFileName().toString().endsWith(".tmp")).toList();
        }
    }
}
