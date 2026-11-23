package io.interviewcodi.backend.practice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.interviewcodi.backend.TestcontainersConfiguration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * 음성 답변 스키마(V5)의 제약을 확인한다. 각 테스트는 끝나면 되돌리고, 제약 위반은 테스트의 마지막 문장에서만 낸다
 * (위반 뒤에는 같은 트랜잭션에서 다른 문장을 실행할 수 없다).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class VoiceAnswerSchemaTest {

    private static final AtomicLong NEXT_KAKAO_USER_ID = new AtomicLong(5_000_000L);

    @Autowired
    private JdbcClient jdbcClient;

    private long practiceId;
    private long questionId;

    @BeforeEach
    void setUp() {
        long userId = insertReturningId("insert into users (kakao_user_id) values (:v) returning id",
                NEXT_KAKAO_USER_ID.getAndIncrement());
        practiceId = insertReturningId(
                "insert into practices (user_id, start_mode) values (:v, 'EXPERIENCE') returning id", userId);
        questionId = insertReturningId("""
                insert into questions (practice_id, order_no, content, source_kind, preset_key)
                values (:v, 1, '프로젝트를 소개해 주세요.', 'EXPERIENCE_PRESET', 'INTRO') returning id
                """, practiceId);
    }

    @Test
    @DisplayName("입력 방식을 정하지 않은 시도는 텍스트로 저장된다")
    void answerDefaultsToText() {
        long answerId = insertAnswer(1, null);

        String inputMode = jdbcClient.sql("select input_mode from answers where id = :id")
                .param("id", answerId)
                .query(String.class)
                .single();

        assertThat(inputMode).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("음성 시도는 버전 없이 녹음과 인식 요청을 가질 수 있다")
    void voiceAnswerHasRecordingAndTranscribeRequestWithoutRevision() {
        long answerId = insertAnswer(1, "VOICE");
        long recordingId = insertRecording(answerId);

        long requestId = insertTranscribeRequest(recordingId);

        Boolean withoutRevision = jdbcClient.sql("select answer_revision_id is null from ai_requests where id = :id")
                .param("id", requestId)
                .query(Boolean.class)
                .single();
        assertThat(withoutRevision).isTrue();
    }

    @Test
    @DisplayName("알 수 없는 입력 방식은 거부한다")
    void rejectsUnknownInputMode() {
        assertThatThrownBy(() -> insertAnswer(1, "VIDEO"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_answers_input_mode");
    }

    @Test
    @DisplayName("한 시도에 녹음을 두 개 둘 수 없다")
    void rejectsSecondRecordingForAnswer() {
        long answerId = insertAnswer(1, "VOICE");
        insertRecording(answerId);

        assertThatThrownBy(() -> insertRecording(answerId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_recordings_answer");
    }

    @Test
    @DisplayName("오디오가 아닌 형식의 녹음은 거부한다")
    void rejectsNonAudioContentType() {
        long answerId = insertAnswer(1, "VOICE");

        assertThatThrownBy(() -> jdbcClient.sql("""
                        insert into recordings (answer_id, storage_key, content_type, duration_ms, size_bytes)
                        values (:answerId, :key, 'text/plain', 1000, 10)
                        """)
                .param("answerId", answerId)
                .param("key", "recordings/" + UUID.randomUUID())
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_recordings_content_type");
    }

    @Test
    @DisplayName("인식 요청에 답변 버전을 함께 넣으면 거부한다")
    void rejectsTranscribeRequestWithRevision() {
        long answerId = insertAnswer(1, "VOICE");
        long recordingId = insertRecording(answerId);
        long revisionId = insertRevision(insertAnswer(2, null));

        assertThatThrownBy(() -> jdbcClient.sql("""
                        insert into ai_requests (practice_id, kind, recording_id, answer_revision_id)
                        values (:practiceId, 'TRANSCRIBE', :recordingId, :revisionId)
                        """)
                .param("practiceId", practiceId)
                .param("recordingId", recordingId)
                .param("revisionId", revisionId)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ai_requests_input");
    }

    @Test
    @DisplayName("피드백 요청은 녹음이 아니라 답변 버전을 가리켜야 한다")
    void rejectsFeedbackRequestWithRecordingOnly() {
        long recordingId = insertRecording(insertAnswer(1, "VOICE"));

        assertThatThrownBy(() -> jdbcClient.sql("""
                        insert into ai_requests (practice_id, kind, recording_id)
                        values (:practiceId, 'FEEDBACK', :recordingId)
                        """)
                .param("practiceId", practiceId)
                .param("recordingId", recordingId)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ai_requests_input");
    }

    @Test
    @DisplayName("같은 녹음의 처리 중 인식 요청은 하나뿐이다")
    void rejectsSecondActiveTranscribeRequest() {
        long recordingId = insertRecording(insertAnswer(1, "VOICE"));
        insertTranscribeRequest(recordingId);

        assertThatThrownBy(() -> insertTranscribeRequest(recordingId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_ai_requests_recording_active");
    }

    @Test
    @DisplayName("실패한 인식 요청이 있어도 같은 녹음을 다시 인식할 수 있다")
    void allowsRetryAfterFailedTranscribeRequest() {
        long recordingId = insertRecording(insertAnswer(1, "VOICE"));
        long failedId = insertTranscribeRequest(recordingId);
        jdbcClient.sql("update ai_requests set status = 'FAILED', finished_at = now() where id = :id")
                .param("id", failedId)
                .update();

        long retryId = insertTranscribeRequest(recordingId);

        assertThat(retryId).isNotEqualTo(failedId);
    }

    @Test
    @DisplayName("여러 녹음의 인식 요청은 버전 기준 유일 인덱스에 걸리지 않는다")
    void transcribeRequestsForDifferentRecordingsCoexist() {
        long first = insertTranscribeRequest(insertRecording(insertAnswer(1, "VOICE")));

        long second = insertTranscribeRequest(insertRecording(insertAnswer(2, "VOICE")));

        assertThat(second).isNotEqualTo(first);
    }

    private long insertAnswer(int attemptNo, String inputMode) {
        String sql = inputMode == null
                ? """
                insert into answers (question_id, attempt_no, client_request_id)
                values (:questionId, :attemptNo, :clientRequestId) returning id
                """
                : """
                insert into answers (question_id, attempt_no, client_request_id, input_mode)
                values (:questionId, :attemptNo, :clientRequestId, :inputMode) returning id
                """;
        JdbcClient.StatementSpec statement = jdbcClient.sql(sql)
                .param("questionId", questionId)
                .param("attemptNo", attemptNo)
                .param("clientRequestId", UUID.randomUUID());
        if (inputMode != null) {
            statement = statement.param("inputMode", inputMode);
        }
        return statement.query(Long.class).single();
    }

    private long insertRecording(long answerId) {
        return jdbcClient.sql("""
                        insert into recordings (answer_id, storage_key, content_type, duration_ms, size_bytes)
                        values (:answerId, :key, 'audio/webm', 62000, 240000)
                        returning id
                        """)
                .param("answerId", answerId)
                .param("key", "recordings/" + UUID.randomUUID() + ".webm")
                .query(Long.class)
                .single();
    }

    private long insertRevision(long answerId) {
        return jdbcClient.sql("""
                        insert into answer_revisions (answer_id, revision_no, content)
                        values (:answerId, 1, '레디스로 캐시를 붙였어요.')
                        returning id
                        """)
                .param("answerId", answerId)
                .query(Long.class)
                .single();
    }

    private long insertTranscribeRequest(long recordingId) {
        return jdbcClient.sql("""
                        insert into ai_requests (practice_id, kind, recording_id)
                        values (:practiceId, 'TRANSCRIBE', :recordingId)
                        returning id
                        """)
                .param("practiceId", practiceId)
                .param("recordingId", recordingId)
                .query(Long.class)
                .single();
    }

    private long insertReturningId(String sql, long value) {
        return jdbcClient.sql(sql).param("v", value).query(Long.class).single();
    }
}
